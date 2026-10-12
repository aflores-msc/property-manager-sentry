package com.propertymanager.sentry.ticket;

import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.TicketDetails;
import java.time.Instant;

/**
 * Read-only projection of a ticket for the UI and CSV export: everything except the raw body, which
 * is loaded on demand. Being a record, it's safe to hand to the JavaFX thread.
 */
public record TicketSummary(
        long id,
        String emailId,
        Instant receivedAt,
        String sender,
        String subject,
        Category category,
        Priority priority,
        TicketStatus status,
        boolean unread,
        String senderType,
        String propertyAddress,
        String unitNumber,
        String rationale,
        TicketDetails details) {

    public TicketSummary withStatus(TicketStatus newStatus) {
        return new TicketSummary(
                id,
                emailId,
                receivedAt,
                sender,
                subject,
                category,
                priority,
                newStatus,
                unread,
                senderType,
                propertyAddress,
                unitNumber,
                rationale,
                details);
    }
}
