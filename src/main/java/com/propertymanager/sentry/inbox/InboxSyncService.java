package com.propertymanager.sentry.inbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.propertymanager.sentry.config.SentryProperties;
import com.propertymanager.sentry.inbox.SyncEvent.SyncSummary;
import com.propertymanager.sentry.mail.InboundEmail;
import com.propertymanager.sentry.mail.MailboxClient;
import com.propertymanager.sentry.mail.MailboxSetupException;
import com.propertymanager.sentry.ticket.TicketService;
import com.propertymanager.sentry.triage.TriageGraph;
import com.propertymanager.sentry.triage.TriageOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Fetches new mail, runs each email through the triage graph and stores the results.
 *
 * <p>Emails are processed in parallel on virtual threads, with a semaphore capping how many
 * LLM conversations run at once ({@code sentry.triage.concurrency}). Only one sync runs at a
 * time; a second request while one is running is ignored.
 */
@Service
public class InboxSyncService {

    private static final Logger log = LoggerFactory.getLogger(InboxSyncService.class);

    private final MailboxClient mailbox;
    private final TriageGraph triageGraph;
    private final TicketService tickets;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SentryProperties.Triage settings;
    private final AtomicBoolean running = new AtomicBoolean();

    public InboxSyncService(MailboxClient mailbox, TriageGraph triageGraph, TicketService tickets,
                            ApplicationEventPublisher events, Clock clock, SentryProperties properties) {
        this.mailbox = mailbox;
        this.triageGraph = triageGraph;
        this.tickets = tickets;
        this.events = events;
        this.clock = clock;
        this.settings = properties.triage();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a sync in the background.
     *
     * @return the running sync, or an already-completed empty future if one was in progress
     */
    public CompletableFuture<SyncSummary> startSync(SyncTrigger trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("{} sync requested while another sync is running; ignoring", trigger);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sync(trigger);
            } finally {
                running.set(false);
            }
        }, task -> Thread.ofVirtual().name("inbox-sync").start(task));
    }

    SyncSummary sync(SyncTrigger trigger) {
        Instant started = clock.instant();
        events.publishEvent(new SyncEvent.Started(trigger));

        List<String> candidates;
        Set<String> known;
        try {
            candidates = mailbox.listCandidateIds();
            known = tickets.knownEmailIds(candidates);
        } catch (MailboxSetupException e) {
            log.warn("Sync aborted: {}", e.getMessage());
            events.publishEvent(new SyncEvent.Aborted(e.getMessage()));
            return null;
        } catch (Exception e) {
            log.error("Sync aborted: could not list mailbox", e);
            events.publishEvent(new SyncEvent.Aborted(e.getMessage()));
            return null;
        }
        List<String> toProcess = candidates.stream().filter(id -> !known.contains(id)).toList();
        events.publishEvent(new SyncEvent.Planned(candidates.size(), known.size(), toProcess.size()));

        var ticketed = new AtomicInteger();
        var ignored = new AtomicInteger();
        var failed = new AtomicInteger();
        var completed = new AtomicInteger();
        var permits = new Semaphore(settings.concurrency());

        // One virtual thread per email; the semaphore, not the pool, bounds parallelism.
        // Closing the executor waits for every task (structured "fork, then join").
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String emailId : toProcess) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        EmailResult result = process(emailId);
                        switch (result.outcome()) {
                            case TriageOutcome.Ticketed _ -> ticketed.incrementAndGet();
                            case TriageOutcome.Ignored _ -> ignored.incrementAndGet();
                            case TriageOutcome.Failed _ -> failed.incrementAndGet();
                        }
                        events.publishEvent(new SyncEvent.EmailTriaged(emailId, result.subject(), result.outcome(),
                                result.ticketId(), completed.incrementAndGet(), toProcess.size()));
                    } finally {
                        permits.release();
                    }
                });
            }
        }

        var summary = new SyncSummary(ticketed.get(), ignored.get(), failed.get(), known.size(),
                Duration.between(started, clock.instant()));
        log.info("Sync finished: {}", summary);
        events.publishEvent(new SyncEvent.Finished(summary));
        return summary;
    }

    private record EmailResult(String subject, TriageOutcome outcome, Long ticketId) {
    }

    private EmailResult process(String emailId) {
        InboundEmail email;
        try {
            email = mailbox.fetch(emailId);
        } catch (Exception e) {
            log.warn("Could not download message {}: {}", emailId, e.toString());
            return new EmailResult("", new TriageOutcome.Failed("download failed: " + e.getMessage()), null);
        }
        TriageOutcome outcome = triageWithTimeout(email);
        try {
            return new EmailResult(email.subject(), outcome, tickets.record(email, outcome));
        } catch (Exception e) {
            log.error("Could not store triage result for {}", emailId, e);
            return new EmailResult(email.subject(), new TriageOutcome.Failed("database error: " + e.getMessage()), null);
        }
    }

    /** Runs the graph on its own virtual thread so it can be interrupted when it takes too long. */
    private TriageOutcome triageWithTimeout(InboundEmail email) {
        var future = new FutureTask<>(() -> triageGraph.triage(email));
        Thread.ofVirtual().name("triage-" + email.id()).start(future);
        try {
            return future.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new TriageOutcome.Failed("timed out after " + settings.timeout().toSeconds() + "s");
        } catch (ExecutionException e) {
            return new TriageOutcome.Failed(String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TriageOutcome.Failed("interrupted");
        }
    }
}
