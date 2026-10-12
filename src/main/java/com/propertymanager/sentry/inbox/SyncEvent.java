package com.propertymanager.sentry.inbox;

import com.propertymanager.sentry.triage.TriageOutcome;
import java.time.Duration;

/**
 * Progress of an inbox sync, published as Spring application events. The UI listens for these, so
 * it reflects every sync the same way, whether started by the button or by the scheduler.
 */
public sealed interface SyncEvent {

    record Started(SyncTrigger trigger) implements SyncEvent {}

    /** Candidate messages are known; {@code toProcess} of them are new and will be triaged. */
    record Planned(int candidates, int alreadyKnown, int toProcess) implements SyncEvent {}

    /** One email finished triage. {@code ticketId} is set when a new ticket was created. */
    record EmailTriaged(
            String emailId,
            String subject,
            TriageOutcome outcome,
            Long ticketId,
            int completed,
            int total)
            implements SyncEvent {}

    record Finished(SyncSummary summary) implements SyncEvent {}

    /** The sync couldn't run at all (e.g. Gmail not configured or unreachable). */
    record Aborted(String message) implements SyncEvent {}

    record SyncSummary(int ticketed, int ignored, int failed, int alreadyKnown, Duration elapsed) {}
}
