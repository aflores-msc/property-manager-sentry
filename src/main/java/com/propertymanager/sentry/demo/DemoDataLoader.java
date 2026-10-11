package com.propertymanager.sentry.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.propertymanager.sentry.mail.InboundEmail;
import com.propertymanager.sentry.ticket.TicketService;
import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.CityNoticeDetails;
import com.propertymanager.sentry.triage.DisputeDetails;
import com.propertymanager.sentry.triage.MaintenanceDetails;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.RoutingDecision;
import com.propertymanager.sentry.triage.TriageOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a few realistic tickets when the {@code demo} profile is active and the database is empty,
 * so the UI can be explored without Gmail or Gemini set up:
 * {@code ./gradlew bootRun --args='--spring.profiles.active=demo'}.
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final TicketService tickets;

    DemoDataLoader(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tickets.findAll().isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        record Sample(InboundEmail email, TriageOutcome outcome) {
        }
        var samples = List.of(
                new Sample(email("demo-1", "Maria Lopez <maria.lopez@example.com>", "URGENT: water pouring from ceiling in 3B",
                        now.minus(Duration.ofMinutes(25)), """
                                Hi, water is coming through the bathroom ceiling and it's getting worse.
                                I put buckets down but the light fixture is dripping too. I'm home all day.
                                Maria, 3B, 1450 Harbor View Ave. 555-0142"""),
                        new TriageOutcome.Ticketed(
                                routing(Category.MAINTENANCE, Priority.URGENT, "tenant", "1450 Harbor View Ave", "3B",
                                        "Active leak near an electrical fixture: risk of damage and shock."),
                                new MaintenanceDetails("Water leaking through the bathroom ceiling, dripping from the light fixture.",
                                        "plumbing", "bathroom ceiling", "getting worse", true, "home all day",
                                        "Maria Lopez", "555-0142"))),
                new Sample(email("demo-2", "Code Enforcement <noreply@city.example.gov>", "Notice of Violation - Case CE-2026-0481",
                        now.minus(Duration.ofHours(5)), "Notice of violation for 220 Elm Street..."),
                        new TriageOutcome.Ticketed(
                                routing(Category.CITY_NOTICE, Priority.HIGH, "city", "220 Elm Street", "",
                                        "Official violation notice with a compliance deadline within two weeks."),
                                new CityNoticeDetails("Smoke detectors missing in common hallway; must be fixed before re-inspection.",
                                        "code violation", "City Code Enforcement", "CE-2026-0481", "2026-10-24",
                                        "Two hallway smoke detectors missing on the 2nd floor.",
                                        "Install detectors and schedule a re-inspection.", "$250 per day after deadline",
                                        "Inspector D. Park", "555-0100", "codeenforcement@city.example.gov"))),
                new Sample(email("demo-3", "Tom Becker <tbecker@example.com>", "Neighbor in 5A again",
                        now.minus(Duration.ofDays(1)), "The music from 5A went until 2am again..."),
                        new TriageOutcome.Ticketed(
                                routing(Category.TENANT_DISPUTE, Priority.MEDIUM, "tenant", "1450 Harbor View Ave", "4A",
                                        "Repeated noise complaint between neighbors, no threats."),
                                new DisputeDetails("Recurring late-night noise from unit 5A.", "noise", "4A (Tom Becker), 5A",
                                        "Loud music until 2am several nights a week.",
                                        "Management warning to 5A about quiet hours.", false))),
                new Sample(email("demo-4", "Sam Ortiz <sam.ortiz@example.com>", "Dishwasher not draining",
                        now.minus(Duration.ofDays(2)), "Our dishwasher leaves standing water..."),
                        new TriageOutcome.Ticketed(
                                routing(Category.MAINTENANCE, Priority.LOW, "tenant", "88 Pine Court", "12",
                                        "Appliance issue with no safety risk."),
                                new MaintenanceDetails("Dishwasher leaves standing water after cycles.", "appliance", "kitchen",
                                        "annoying", false, "weekdays after 6pm", "Sam Ortiz", ""))));
        samples.forEach(sample -> tickets.record(sample.email(), sample.outcome()));
        log.info("Demo profile: seeded {} sample tickets", samples.size());
    }

    private static InboundEmail email(String id, String from, String subject, Instant received, String body) {
        return new InboundEmail(id, from, subject, received, body);
    }

    private static RoutingDecision routing(Category category, Priority priority, String senderType, String address,
                                           String unit, String rationale) {
        return new RoutingDecision(category, priority, senderType, address, unit, rationale);
    }
}
