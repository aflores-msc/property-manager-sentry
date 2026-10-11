package com.propertymanager.sentry.mail;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * An email pulled from the mailbox, already reduced to what triage needs.
 *
 * @param id         provider message id (stable, used for de-duplication)
 * @param sender     raw {@code From} header
 * @param subject    subject line
 * @param receivedAt when the provider received the message (not when we processed it)
 * @param body       plain-text body
 *
 * <p>{@link Serializable} because it travels through the LangGraph4j state, which is copied
 * using Java serialization.
 */
public record InboundEmail(String id, String sender, String subject, Instant receivedAt, String body)
        implements Serializable {

    public InboundEmail {
        Objects.requireNonNull(id, "id");
        sender = Objects.requireNonNullElse(sender, "");
        subject = Objects.requireNonNullElse(subject, "(no subject)");
        receivedAt = Objects.requireNonNullElseGet(receivedAt, Instant::now);
        body = Objects.requireNonNullElse(body, "");
    }
}
