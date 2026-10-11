package com.propertymanager.sentry.triage;

import java.io.Serializable;

/**
 * Category-specific data extracted by a specialist node.
 *
 * <p>Sealed, so a {@code switch} over the details is checked for exhaustiveness by the compiler.
 * The LLM is always asked for one concrete record, never for this interface.
 *
 * <p>{@link Serializable} because LangGraph4j copies the graph state between steps using Java
 * serialization.
 */
public sealed interface TicketDetails extends Serializable
        permits MaintenanceDetails, CityNoticeDetails, DisputeDetails {

    /** One-line summary shown in the detail panel header. */
    String summary();
}
