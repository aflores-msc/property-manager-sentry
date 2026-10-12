package com.propertymanager.sentry.inbox;

import static com.propertymanager.sentry.triage.Fixtures.routing;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.propertymanager.sentry.mail.InboundEmail;
import com.propertymanager.sentry.mail.MailboxClient;
import com.propertymanager.sentry.ticket.TicketService;
import com.propertymanager.sentry.ticket.TicketStatus;
import com.propertymanager.sentry.ticket.TicketSummary;
import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.CityNoticeDetails;
import com.propertymanager.sentry.triage.DisputeDetails;
import com.propertymanager.sentry.triage.Fixtures;
import com.propertymanager.sentry.triage.MaintenanceDetails;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.RoutingDecision;
import com.propertymanager.sentry.triage.TriageAgents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full Spring context (Flyway, JPA on in-memory H2, the real LangGraph4j graph) with a fake
 * mailbox and fake LLM agents, so it runs offline and deterministically.
 */
@SpringBootTest(properties = {
        "sentry.user-config=build/no-user-config.yml",
        "sentry.data-dir=build/test-data",
        "spring.datasource.url=jdbc:h2:mem:sentry-it;DB_CLOSE_DELAY=-1",
        "spring.ai.google.genai.api-key=test-key",
        "logging.file.name="
})
class InboxSyncServiceIT {

    @Autowired
    InboxSyncService syncService;
    @Autowired
    TicketService tickets;
    @Autowired
    FakeMailbox mailbox;
    @Autowired
    ScriptedAgents agents;
    @Autowired
    EventRecorder events;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("delete from ticket");
        jdbc.update("delete from processed_email");
        mailbox.emails.clear();
        mailbox.labels.clear();
        mailbox.failLabeling = false;
        mailbox.readOnly = false;
        agents.routes.clear();
        agents.failures.clear();
        agents.routeCalls.set(0);
        events.received.clear();
    }

    @Test
    void createsTicketsSkipsIgnoredAndRetriesFailures() {
        mailbox.readOnly = true; // labels off: de-duplication relies on the local processed_email table
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        mailbox.add(Fixtures.email("m2", "Code violation notice"));
        mailbox.add(Fixtures.email("m3", "Noisy neighbors"));
        mailbox.add(Fixtures.email("m4", "Your weekly newsletter"));
        mailbox.add(Fixtures.email("m5", "Something the model chokes on"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.URGENT));
        agents.routes.put("m2", routing(Category.CITY_NOTICE, Priority.HIGH));
        agents.routes.put("m3", routing(Category.TENANT_DISPUTE, Priority.MEDIUM));
        agents.routes.put("m4", routing(Category.IGNORED, Priority.LOW));
        agents.failures.add("m5");

        var first = syncService.startSync(SyncTrigger.MANUAL).join();

        assertThat(first.ticketed()).isEqualTo(3);
        assertThat(first.ignored()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.alreadyKnown()).isZero();

        List<TicketSummary> saved = tickets.findAll();
        assertThat(saved).extracting(TicketSummary::emailId).containsExactlyInAnyOrder("m1", "m2", "m3");
        assertThat(saved).allMatch(TicketSummary::unread).allMatch(t -> t.status() == TicketStatus.OPEN);
        TicketSummary leak = saved.stream().filter(t -> t.emailId().equals("m1")).findFirst().orElseThrow();
        assertThat(leak.details()).isInstanceOf(MaintenanceDetails.class); // sealed type survives the JSON column
        assertThat(leak.propertyAddress()).isEqualTo("12 Oak St");
        assertThat(tickets.rawBody(leak.id())).isEqualTo("Body of Sink leaking");

        // Second sync: known emails are not sent to the model again; the failed one is retried.
        agents.failures.clear();
        agents.routes.put("m5", routing(Category.MAINTENANCE, Priority.LOW));
        agents.routeCalls.set(0);

        var second = syncService.startSync(SyncTrigger.SCHEDULED).join();

        assertThat(second.alreadyKnown()).isEqualTo(4);
        assertThat(second.ticketed()).isEqualTo(1);
        assertThat(agents.routeCalls).hasValue(1);
        assertThat(tickets.findAll()).hasSize(4);
    }

    @Test
    void labelsTriagedEmailsSoTheyAreNotListedAgain() {
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        mailbox.add(Fixtures.email("m2", "Newsletter"));
        mailbox.add(Fixtures.email("m3", "Model error"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.HIGH));
        agents.routes.put("m2", routing(Category.IGNORED, Priority.LOW));
        agents.failures.add("m3");

        syncService.startSync(SyncTrigger.MANUAL).join();

        assertThat(mailbox.labels).containsOnly(
                Map.entry("m1", "Maintenance"),
                Map.entry("m2", FakeMailbox.PROCESSED_ONLY)); // m3 failed: left unlabeled for a retry
        var second = syncService.startSync(SyncTrigger.MANUAL).join();
        assertThat(second.alreadyKnown()).isZero(); // labeled ones are no longer listed at all
        assertThat(agents.routeCalls).hasValue(4);  // m1, m2, m3, then only m3 again
    }

    @Test
    void labelsKnownEmailsThatAreStillListed() {
        // Processed while labeling was off (or a label call failed): known locally, still listed by Gmail.
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.HIGH));
        mailbox.failLabeling = true;
        syncService.startSync(SyncTrigger.MANUAL).join();
        assertThat(mailbox.labels).isEmpty();

        mailbox.failLabeling = false;
        mailbox.readOnly = false;
        var second = syncService.startSync(SyncTrigger.MANUAL).join();

        assertThat(second.alreadyKnown()).isEqualTo(1);
        assertThat(agents.routeCalls).hasValue(1); // not re-triaged
        assertThat(mailbox.labels).containsOnly(Map.entry("m1", FakeMailbox.PROCESSED_ONLY));
    }

    @Test
    void labelingFailureDoesNotLoseTheTicket() {
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.HIGH));
        mailbox.failLabeling = true;

        var summary = syncService.startSync(SyncTrigger.MANUAL).join();

        assertThat(summary.ticketed()).isEqualTo(1);
        assertThat(tickets.findAll()).hasSize(1);
    }

    @Test
    void publishesProgressEventsInOrder() {
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.HIGH));

        syncService.startSync(SyncTrigger.MANUAL).join();

        assertThat(events.received).hasExactlyElementsOfTypes(
                SyncEvent.Started.class, SyncEvent.Planned.class, SyncEvent.EmailTriaged.class, SyncEvent.Finished.class);
    }

    @Test
    void statusAndReadFlagArePersisted() {
        mailbox.add(Fixtures.email("m1", "Sink leaking"));
        agents.routes.put("m1", routing(Category.MAINTENANCE, Priority.HIGH));
        syncService.startSync(SyncTrigger.MANUAL).join();
        long id = tickets.findAll().getFirst().id();

        tickets.updateStatus(id, TicketStatus.IN_PROGRESS);
        tickets.markRead(id);

        assertThat(tickets.findAll().getFirst())
                .returns(TicketStatus.IN_PROGRESS, TicketSummary::status)
                .returns(false, TicketSummary::unread);
    }

    @Test
    void abortsCleanlyWhenTheMailboxIsUnavailable() {
        mailbox.broken = true;
        try {
            assertThat(syncService.startSync(SyncTrigger.MANUAL).join()).isNull();
            assertThat(events.received).last().isInstanceOf(SyncEvent.Aborted.class);
            assertThat(syncService.isRunning()).isFalse();
        } finally {
            mailbox.broken = false;
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        FakeMailbox fakeMailbox() {
            return new FakeMailbox();
        }

        @Bean
        @Primary
        ScriptedAgents scriptedAgents() {
            return new ScriptedAgents();
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    /** In-memory mailbox that, like Gmail with labels on, stops listing messages once labeled. */
    static class FakeMailbox implements MailboxClient {
        static final String PROCESSED_ONLY = "(processed)";

        final Map<String, InboundEmail> emails = new LinkedHashMap<>();
        final Map<String, String> labels = new ConcurrentHashMap<>();
        volatile boolean broken;
        volatile boolean failLabeling;
        volatile boolean readOnly;

        void add(InboundEmail email) {
            emails.put(email.id(), email);
        }

        @Override
        public List<String> listCandidateIds() throws IOException {
            if (broken) {
                throw new IOException("Gmail unreachable");
            }
            return emails.keySet().stream().filter(id -> !labels.containsKey(id)).toList();
        }

        @Override
        public void markTriaged(Collection<String> messageIds, String category) throws IOException {
            if (failLabeling) {
                throw new IOException("403 insufficient permissions");
            }
            if (readOnly) {
                return;
            }
            messageIds.forEach(id -> labels.put(id, category == null ? PROCESSED_ONLY : category));
        }

        @Override
        public InboundEmail fetch(String messageId) {
            return emails.get(messageId);
        }
    }

    static class ScriptedAgents implements TriageAgents {
        final Map<String, RoutingDecision> routes = new ConcurrentHashMap<>();
        final List<String> failures = new CopyOnWriteArrayList<>();
        final AtomicInteger routeCalls = new AtomicInteger();

        @Override
        public RoutingDecision route(InboundEmail email) {
            routeCalls.incrementAndGet();
            if (failures.contains(email.id())) {
                throw new IllegalStateException("model error");
            }
            return routes.get(email.id());
        }

        @Override
        public MaintenanceDetails extractMaintenance(InboundEmail email, RoutingDecision routing) {
            return Fixtures.maintenance();
        }

        @Override
        public CityNoticeDetails extractCityNotice(InboundEmail email, RoutingDecision routing) {
            return Fixtures.cityNotice();
        }

        @Override
        public DisputeDetails extractDispute(InboundEmail email, RoutingDecision routing) {
            return Fixtures.dispute();
        }
    }

    static class EventRecorder {
        final List<SyncEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(SyncEvent event) {
            received.add(event);
        }
    }
}
