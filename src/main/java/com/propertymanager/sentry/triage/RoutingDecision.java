package com.propertymanager.sentry.triage;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Output of the supervisor node: the envelope every email gets before routing. Spring AI turns
 * this record (and its descriptions) into the JSON schema Gemini must answer with.
 */
public record RoutingDecision(
        @JsonPropertyDescription("Exactly one category for the email. Use IGNORED for anything that is not about physical property management: newsletters, marketing, account/security alerts, receipts, personal mail.")
        Category category,
        @JsonPropertyDescription("How urgent the email is for the property manager.")
        Priority priority,
        @JsonPropertyDescription("Who sent it, e.g. tenant, city, vendor, contractor, owner, unknown.")
        String senderType,
        @JsonPropertyDescription("Street address of the property the email is about, or empty if not stated.")
        String propertyAddress,
        @JsonPropertyDescription("Unit or apartment number, or empty if not stated.")
        String unitNumber,
        @JsonPropertyDescription("One sentence explaining why this category and priority were chosen.")
        String rationale) implements Serializable {
}
