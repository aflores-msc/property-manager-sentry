package com.propertymanager.sentry.triage;

import static com.propertymanager.sentry.triage.Fixtures.email;
import static com.propertymanager.sentry.triage.Fixtures.routing;
import static org.assertj.core.api.Assertions.assertThat;

import com.propertymanager.sentry.mail.InboundEmail;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Exercises the LangGraph4j wiring with fake agents: no LLM involved. */
class TriageGraphTest {

    @Test
    void routesEachCategoryToItsSpecialist() throws Exception {
        assertTicketed(Category.MAINTENANCE, MaintenanceDetails.class, "maintenance");
        assertTicketed(Category.CITY_NOTICE, CityNoticeDetails.class, "cityNotice");
        assertTicketed(Category.TENANT_DISPUTE, DisputeDetails.class, "dispute");
    }

    @Test
    void ignoredEmailsStopAfterTheSupervisor() throws Exception {
        var agents = new FakeAgents(_ -> routing(Category.IGNORED, Priority.LOW));

        var outcome = new TriageGraph(agents).triage(email("1", "Weekly newsletter"));

        assertThat(outcome).isInstanceOf(TriageOutcome.Ignored.class);
        assertThat(agents.calls).containsExactly("route");
    }

    @Test
    void supervisorFailureBecomesFailedOutcome() throws Exception {
        var agents =
                new FakeAgents(
                        _ -> {
                            throw new IllegalStateException("model unavailable");
                        });

        var outcome = new TriageGraph(agents).triage(email("1", "Leak"));

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.Failed.class,
                        failed -> assertThat(failed.reason()).contains("model unavailable"));
    }

    @Test
    void specialistFailureBecomesFailedOutcome() throws Exception {
        var agents = new FakeAgents(_ -> routing(Category.MAINTENANCE, Priority.HIGH));
        agents.failSpecialists = true;

        var outcome = new TriageGraph(agents).triage(email("1", "Leak"));

        assertThat(outcome).isInstanceOf(TriageOutcome.Failed.class);
    }

    @Test
    void incompleteRoutingDecisionIsRejected() throws Exception {
        var agents = new FakeAgents(_ -> new RoutingDecision(null, Priority.LOW, "", "", "", ""));

        assertThat(new TriageGraph(agents).triage(email("1", "?")))
                .isInstanceOf(TriageOutcome.Failed.class);
    }

    private static void assertTicketed(
            Category category, Class<? extends TicketDetails> detailsType, String specialistCall)
            throws Exception {
        var agents = new FakeAgents(_ -> routing(category, Priority.HIGH));

        var outcome = new TriageGraph(agents).triage(email("1", "subject"));

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        TriageOutcome.Ticketed.class,
                        ticketed -> {
                            assertThat(ticketed.routing().category()).isEqualTo(category);
                            assertThat(ticketed.details()).isInstanceOf(detailsType);
                        });
        assertThat(agents.calls).containsExactly("route", specialistCall);
    }

    static final class FakeAgents implements TriageAgents {

        final List<String> calls = new ArrayList<>();
        final Function<InboundEmail, RoutingDecision> router;
        boolean failSpecialists;

        FakeAgents(Function<InboundEmail, RoutingDecision> router) {
            this.router = router;
        }

        @Override
        public synchronized RoutingDecision route(InboundEmail email) {
            calls.add("route");
            return router.apply(email);
        }

        @Override
        public synchronized MaintenanceDetails extractMaintenance(
                InboundEmail email, RoutingDecision routing) {
            calls.add("maintenance");
            failIfAsked();
            return Fixtures.maintenance();
        }

        @Override
        public synchronized CityNoticeDetails extractCityNotice(
                InboundEmail email, RoutingDecision routing) {
            calls.add("cityNotice");
            failIfAsked();
            return Fixtures.cityNotice();
        }

        @Override
        public synchronized DisputeDetails extractDispute(
                InboundEmail email, RoutingDecision routing) {
            calls.add("dispute");
            failIfAsked();
            return Fixtures.dispute();
        }

        private void failIfAsked() {
            if (failSpecialists) {
                throw new IllegalStateException("specialist failed");
            }
        }
    }
}
