package com.propertymanager.sentry.triage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Extracted by the legal / city-notice specialist. */
public record CityNoticeDetails(
        @JsonPropertyDescription("Short summary of the notice in plain words.") String summary,
        @JsonPropertyDescription(
                        "Type of notice, e.g. code violation, inspection, permit, tax, zoning.")
                String noticeType,
        @JsonPropertyDescription("City department or agency that issued the notice.")
                String issuingAuthority,
        @JsonPropertyDescription("Case, reference or permit number, or empty if not stated.")
                String referenceNumber,
        @JsonPropertyDescription(
                        "Compliance or response deadline as an ISO-8601 date (YYYY-MM-DD), or empty"
                                + " if none is stated.")
                String deadline,
        @JsonPropertyDescription("What was found or what the notice is about.")
                String violationDescription,
        @JsonPropertyDescription("What the property manager must do.") String requiredAction,
        @JsonPropertyDescription("Fine or penalty amount mentioned, or empty if none.")
                String potentialFine,
        @JsonPropertyDescription("Contact person or department at the issuing authority, or empty.")
                String contactName,
        @JsonPropertyDescription("Contact phone at the issuing authority, or empty.")
                String contactPhone,
        @JsonPropertyDescription("Contact email at the issuing authority, or empty.")
                String contactEmail)
        implements TicketDetails {}
