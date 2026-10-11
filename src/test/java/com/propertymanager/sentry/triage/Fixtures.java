package com.propertymanager.sentry.triage;

import java.time.Instant;

import com.propertymanager.sentry.mail.InboundEmail;

/** Sample emails and model answers shared by the triage tests. */
public final class Fixtures {

    private Fixtures() {
    }

    public static InboundEmail email(String id, String subject) {
        return new InboundEmail(id, "Jane Tenant <jane@example.com>", subject,
                Instant.parse("2026-10-01T09:30:00Z"), "Body of " + subject);
    }

    public static RoutingDecision routing(Category category, Priority priority) {
        return new RoutingDecision(category, priority, "tenant", "12 Oak St", "4B", "test rationale");
    }

    public static MaintenanceDetails maintenance() {
        return new MaintenanceDetails("Kitchen sink leaking", "plumbing", "kitchen", "bad", true,
                "weekdays after 5pm", "Jane", "555-0100");
    }

    public static CityNoticeDetails cityNotice() {
        return new CityNoticeDetails("Smoke detector violation", "code violation", "City Housing Dept",
                "CV-123", "2026-11-01", "Missing smoke detectors", "Install detectors", "$500",
                "Inspector Lee", "555-0199", "lee@city.example");
    }

    public static DisputeDetails dispute() {
        return new DisputeDetails("Noise complaint", "noise", "4B vs 5B", "Loud music at night",
                "Ask 5B to keep quiet", false);
    }
}
