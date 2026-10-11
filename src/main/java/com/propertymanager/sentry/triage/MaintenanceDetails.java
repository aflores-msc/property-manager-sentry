package com.propertymanager.sentry.triage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Extracted by the maintenance specialist. */
public record MaintenanceDetails(
        @JsonPropertyDescription("Short summary of the request in plain words.")
        String summary,
        @JsonPropertyDescription("Kind of issue, e.g. plumbing, electrical, HVAC, appliance, pest, structural.")
        String issueType,
        @JsonPropertyDescription("Where in the unit or building the problem is.")
        String locationInUnit,
        @JsonPropertyDescription("How severe the tenant says it is, in their words.")
        String reportedSeverity,
        @JsonPropertyDescription("Whether there is a risk to safety or of property damage (water leak, gas, no heat in winter, fire, security).")
        boolean safetyRisk,
        @JsonPropertyDescription("When the tenant is available for a repair visit, or empty if not stated.")
        String tenantAvailability,
        @JsonPropertyDescription("Name of the tenant reporting the issue, or empty if not stated.")
        String tenantName,
        @JsonPropertyDescription("Phone number to reach the tenant, or empty if not stated.")
        String tenantPhone) implements TicketDetails {
}
