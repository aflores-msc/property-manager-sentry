package com.propertymanager.sentry.ticket;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;
import java.util.SequencedSet;

import org.springframework.stereotype.Component;

/**
 * Exports tickets to CSV: fixed columns first, then the union of every detail field found in any
 * ticket (a maintenance ticket simply leaves the city-notice columns blank).
 */
@Component
public class TicketCsvExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public void export(List<TicketSummary> tickets, Path target) throws IOException {
        List<SequencedMap<String, String>> rows = tickets.stream().map(TicketCsvExporter::toRow).toList();
        SequencedSet<String> headers = new LinkedHashSet<>();
        rows.forEach(row -> headers.addAll(row.keySet()));

        try (Writer out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write('﻿'); // BOM, so Excel opens UTF-8 correctly
            writeLine(out, new ArrayList<>(headers));
            for (var row : rows) {
                writeLine(out, headers.stream().map(header -> row.getOrDefault(header, "")).toList());
            }
        }
    }

    static SequencedMap<String, String> toRow(TicketSummary ticket) {
        SequencedMap<String, String> row = new LinkedHashMap<>();
        row.put("Received", DATE.format(ticket.receivedAt()));
        row.put("Sender", ticket.sender());
        row.put("Subject", ticket.subject());
        row.put("Category", ticket.category().label());
        row.put("Priority", ticket.priority().label());
        row.put("Status", ticket.status().label());
        row.put("Property Address", nullToEmpty(ticket.propertyAddress()));
        row.put("Unit", nullToEmpty(ticket.unitNumber()));
        row.put("Sender Type", nullToEmpty(ticket.senderType()));
        TicketFields.of(ticket.details()).forEach(row::putIfAbsent);
        return row;
    }

    private static void writeLine(Writer out, List<String> cells) throws IOException {
        out.write(String.join(",", cells.stream().map(TicketCsvExporter::escape).toList()));
        out.write("\r\n");
    }

    static String escape(String cell) {
        String value = cell == null ? "" : cell;
        // Neutralize spreadsheet formulas (CSV injection): email content is untrusted.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
