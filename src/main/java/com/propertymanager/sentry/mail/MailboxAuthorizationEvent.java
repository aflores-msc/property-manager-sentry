package com.propertymanager.sentry.mail;

/**
 * Published (as a Spring event) while the mailbox waits for the user to grant access in a
 * browser, so the UI can open the page and show the link instead of the app silently waiting.
 */
public sealed interface MailboxAuthorizationEvent {

    /** The user must open {@code url} and approve access. */
    record Required(String url) implements MailboxAuthorizationEvent {
    }

    /** The wait is over: access was granted, or it was cancelled, timed out or failed. */
    record Finished(boolean granted) implements MailboxAuthorizationEvent {
    }
}
