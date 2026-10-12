package com.propertymanager.sentry.ticket;

import com.propertymanager.sentry.mail.InboundEmail;
import com.propertymanager.sentry.triage.TriageOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All ticket reads and writes. Everything the UI or the sync needs from the database goes through
 * here.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final ProcessedEmailRepository processedEmails;
    private final Clock clock;

    public TicketService(
            TicketRepository tickets, ProcessedEmailRepository processedEmails, Clock clock) {
        this.tickets = tickets;
        this.processedEmails = processedEmails;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> findAll() {
        return tickets.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public String rawBody(long ticketId) {
        return tickets.findRawBody(ticketId).orElse("");
    }

    @Transactional(readOnly = true)
    public Set<String> knownEmailIds(Collection<String> emailIds) {
        return emailIds.isEmpty() ? Set.of() : processedEmails.findKnownIds(emailIds);
    }

    /**
     * Stores the result of triaging one email. The ticket (if any) and the "processed" marker are
     * written in a single transaction, so they can never get out of sync. A failed triage stores
     * nothing, so the email is picked up again by the next sync.
     *
     * @return the new ticket's id, or {@code null} if no ticket was created
     */
    @Transactional
    public Long record(InboundEmail email, TriageOutcome outcome) {
        Instant now = clock.instant();
        return switch (outcome) {
            case TriageOutcome.Ticketed(var routing, var details) -> {
                var ticket =
                        tickets.save(
                                new Ticket(
                                        email.id(),
                                        email.receivedAt(),
                                        now,
                                        email.sender(),
                                        email.subject(),
                                        routing.category(),
                                        routing.priority(),
                                        routing.senderType(),
                                        routing.propertyAddress(),
                                        routing.unitNumber(),
                                        routing.rationale(),
                                        details,
                                        email.body()));
                processedEmails.save(
                        new ProcessedEmail(email.id(), ProcessedEmail.Outcome.TICKETED, now));
                yield ticket.getId();
            }
            case TriageOutcome.Ignored _ -> {
                processedEmails.save(
                        new ProcessedEmail(email.id(), ProcessedEmail.Outcome.IGNORED, now));
                yield null;
            }
            case TriageOutcome.Failed _ -> null;
        };
    }

    @Transactional
    public void updateStatus(long ticketId, TicketStatus status) {
        tickets.updateStatus(ticketId, status);
    }

    @Transactional
    public void markRead(long ticketId) {
        tickets.markRead(ticketId);
    }
}
