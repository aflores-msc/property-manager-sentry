package com.propertymanager.sentry.mail;

import java.io.IOException;

/**
 * The mailbox can't be used because local setup is missing. The message is shown to the user as-is.
 */
public class MailboxSetupException extends IOException {

    public MailboxSetupException(String message) {
        super(message);
    }
}
