package com.propertymanager.sentry.triage;

import com.propertymanager.sentry.mail.InboundEmail;

/**
 * The LLM-backed steps the triage graph is made of: one supervisor that routes, and one
 * specialist per category that extracts details.
 *
 * <p>The graph only depends on this interface, so its routing logic can be tested with plain
 * fakes and no model calls. {@link GeminiTriageAgents} is the real implementation.
 */
public interface TriageAgents {

    RoutingDecision route(InboundEmail email);

    MaintenanceDetails extractMaintenance(InboundEmail email, RoutingDecision routing);

    CityNoticeDetails extractCityNotice(InboundEmail email, RoutingDecision routing);

    DisputeDetails extractDispute(InboundEmail email, RoutingDecision routing);
}
