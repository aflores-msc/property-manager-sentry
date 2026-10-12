package com.propertymanager.sentry.mail;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Source of emails to triage. Gmail is the only implementation, but keeping it behind an
 * interface lets the sync logic be tested without network access or OAuth.
 */
public interface MailboxClient {

    /**
     * Returns the ids of messages matching the configured query, newest first, capped at the
     * configured maximum. Cheap: no message bodies are downloaded.
     */
    List<String> listCandidateIds() throws IOException;

    /** Downloads one message in full. */
    InboundEmail fetch(String messageId) throws IOException;

    /**
     * Records in the mailbox itself that these messages went through triage, so later calls to
     * {@link #listCandidateIds()} leave them out. Mailboxes that can't do this ignore the call.
     *
     * @param messageIds the triaged messages
     * @param category   display name of the ticket category (e.g. "City Notice"), or {@code null}
     *                   for messages that were processed without creating a ticket
     */
    default void markTriaged(Collection<String> messageIds, String category) throws IOException {
    }

    /** Stops waiting for a pending browser authorization (see {@link MailboxAuthorizationEvent}), if any. */
    default void cancelAuthorization() {
    }
}
