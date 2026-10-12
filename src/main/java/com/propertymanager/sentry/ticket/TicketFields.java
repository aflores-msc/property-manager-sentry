package com.propertymanager.sentry.ticket;

import com.propertymanager.sentry.triage.TicketDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns ticket details into labeled display values. Shared by the detail panel and CSV export, so
 * they always match.
 */
public final class TicketFields {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TicketFields() {}

    /**
     * Every field of the details record except {@code summary}, as "Human Label" to display value,
     * in declaration order. Booleans become Yes/No.
     */
    public static SequencedMap<String, String> of(TicketDetails details) {
        SequencedMap<String, String> fields = new LinkedHashMap<>();
        if (details == null) {
            return fields;
        }
        JsonNode tree = JSON.valueToTree(details);
        for (Map.Entry<String, JsonNode> field : tree.properties()) {
            String name = field.getKey();
            if (name.equals("summary")) {
                continue;
            }
            JsonNode value = field.getValue();
            String text =
                    value.isBoolean()
                            ? (value.booleanValue() ? "Yes" : "No")
                            : value.isNull() ? "" : value.asString().strip();
            fields.put(humanize(name), text);
        }
        return fields;
    }

    /** {@code contactPhone} becomes {@code Contact Phone}. */
    public static String humanize(String camelCase) {
        String spaced = camelCase.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
