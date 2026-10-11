package com.propertymanager.sentry.triage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Extracted by the tenant-dispute specialist. */
public record DisputeDetails(
        @JsonPropertyDescription("Short summary of the dispute in plain words.")
        String summary,
        @JsonPropertyDescription("Type of dispute, e.g. noise, neighbor, lease terms, deposit, rent, harassment.")
        String disputeType,
        @JsonPropertyDescription("Names or unit numbers of everyone involved.")
        String partiesInvolved,
        @JsonPropertyDescription("The complaint, summarized.")
        String complaint,
        @JsonPropertyDescription("What the sender wants to happen.")
        String desiredResolution,
        @JsonPropertyDescription("True if the email mentions lawyers, lawsuits, police, threats, discrimination or withholding rent, so management or legal should get involved.")
        boolean escalationNeeded) implements TicketDetails {
}
