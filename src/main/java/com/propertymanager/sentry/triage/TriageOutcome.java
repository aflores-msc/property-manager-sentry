package com.propertymanager.sentry.triage;

import java.util.Objects;

/** Result of running one email through the triage graph. */
public sealed interface TriageOutcome {

    /** A property-management email, fully extracted: becomes a ticket. */
    record Ticketed(RoutingDecision routing, TicketDetails details) implements TriageOutcome {
        public Ticketed {
            Objects.requireNonNull(routing, "routing");
            Objects.requireNonNull(details, "details");
        }
    }

    /** Not property-management mail. Remembered so it isn't re-classified, but never shown. */
    record Ignored(RoutingDecision routing) implements TriageOutcome {
    }

    /**
     * Any step failed (model error, timeout, invalid output). Not remembered, so the email is
     * retried on the next sync instead of being stored half-processed.
     */
    record Failed(String reason) implements TriageOutcome {
    }
}
