package com.propertymanager.sentry.mail;

import java.io.IOException;
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
}
