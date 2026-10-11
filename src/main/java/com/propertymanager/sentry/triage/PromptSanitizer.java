package com.propertymanager.sentry.triage;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import com.propertymanager.sentry.mail.InboundEmail;

/**
 * Builds the untrusted-email block that goes into every prompt.
 *
 * <p>Email text is attacker-controlled: anyone can mail the inbox. The system prompt tells the
 * model that everything inside {@code <email>...</email>} is data, never instructions. This class
 * makes sure the email can't close that block early by removing any {@code <email>} or
 * {@code </email>} tags it contains, and it truncates very long bodies to bound cost.
 */
final class PromptSanitizer {

    private static final Pattern EMAIL_TAG = Pattern.compile("(?i)</?\\s*email\\s*>");

    private PromptSanitizer() {
    }

    static String wrapEmail(InboundEmail email, int maxBodyChars, DateTimeFormatter receivedFormat) {
        String body = email.body();
        if (body.length() > maxBodyChars) {
            body = body.substring(0, maxBodyChars) + "\n[... truncated ...]";
        }
        return """
                <email>
                From: %s
                Subject: %s
                Received: %s

                %s
                </email>""".formatted(
                clean(email.sender()),
                clean(email.subject()),
                receivedFormat.format(email.receivedAt()),
                clean(body));
    }

    static String describeProperty(RoutingDecision routing) {
        String address = routing.propertyAddress() == null || routing.propertyAddress().isBlank()
                ? "unknown" : routing.propertyAddress();
        String unit = routing.unitNumber() == null || routing.unitNumber().isBlank()
                ? "" : ", unit " + routing.unitNumber();
        return "The supervisor already identified the property as: " + clean(address + unit) + ".";
    }

    static String clean(String text) {
        return EMAIL_TAG.matcher(text).replaceAll("[email-tag removed]");
    }
}
