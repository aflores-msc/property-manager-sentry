package com.propertymanager.sentry.ticket;

import com.propertymanager.sentry.triage.CityNoticeDetails;
import com.propertymanager.sentry.triage.DisputeDetails;
import com.propertymanager.sentry.triage.MaintenanceDetails;
import com.propertymanager.sentry.triage.TicketDetails;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores the sealed {@link TicketDetails} as JSON text: {@code {"kind": "...", "data": {...}}}.
 *
 * <p>The discriminator lives here rather than as Jackson annotations on the records, because those
 * records double as the response schema sent to Gemini and must not carry a type field. Exhaustive
 * {@code switch}es keep both directions in step with the sealed hierarchy: a new details type won't
 * compile until it's handled here.
 */
@Converter
public class TicketDetailsConverter implements AttributeConverter<TicketDetails, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(TicketDetails details) {
        if (details == null) {
            return null;
        }
        String kind =
                switch (details) {
                    case MaintenanceDetails _ -> "maintenance";
                    case CityNoticeDetails _ -> "cityNotice";
                    case DisputeDetails _ -> "dispute";
                };
        var node = JSON.createObjectNode();
        node.put("kind", kind);
        node.set("data", JSON.valueToTree(details));
        return JSON.writeValueAsString(node);
    }

    @Override
    public TicketDetails convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node = JSON.readTree(json);
        Class<? extends TicketDetails> type =
                switch (node.path("kind").asString()) {
                    case "maintenance" -> MaintenanceDetails.class;
                    case "cityNotice" -> CityNoticeDetails.class;
                    case "dispute" -> DisputeDetails.class;
                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown ticket details kind in: " + json);
                };
        return JSON.treeToValue(node.get("data"), type);
    }
}
