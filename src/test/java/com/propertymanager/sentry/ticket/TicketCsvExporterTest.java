package com.propertymanager.sentry.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.Fixtures;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.TicketDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketCsvExporterTest {

    @Test
    void escapesQuotesCommasAndSpreadsheetFormulas() {
        assertThat(TicketCsvExporter.escape("plain")).isEqualTo("plain");
        assertThat(TicketCsvExporter.escape("a, \"b\"")).isEqualTo("\"a, \"\"b\"\"\"");
        assertThat(TicketCsvExporter.escape("=HYPERLINK(\"evil\")")).startsWith("\"'=HYPERLINK");
        assertThat(TicketCsvExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void writesFixedColumnsThenTheUnionOfDetailFields(@TempDir Path dir) throws Exception {
        var target = dir.resolve("tickets.csv");

        new TicketCsvExporter().export(List.of(
                ticket(1, Category.MAINTENANCE, Fixtures.maintenance()),
                ticket(2, Category.CITY_NOTICE, Fixtures.cityNotice())), target);

        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(3);
        assertThat(lines.getFirst()).startsWith("﻿Received,Sender,Subject,Category,Priority,Status,Property Address,Unit,Sender Type,Issue Type");
        assertThat(lines.getFirst()).contains("Safety Risk", "Issuing Authority", "Contact Phone");
        assertThat(lines.get(1)).contains("Maintenance", "Urgent", "Yes");
        assertThat(lines.get(2)).contains("City Notice", "City Housing Dept");
    }

    @Test
    void humanizesCamelCase() {
        assertThat(TicketFields.humanize("contactPhone")).isEqualTo("Contact Phone");
        assertThat(TicketFields.humanize("deadline")).isEqualTo("Deadline");
    }

    private static TicketSummary ticket(long id, Category category, TicketDetails details) {
        return new TicketSummary(id, "email-" + id, Instant.parse("2026-10-01T09:30:00Z"), "Jane <jane@example.com>",
                "Subject " + id, category, Priority.URGENT, TicketStatus.OPEN, true, "tenant", "12 Oak St", "4B",
                "why", details);
    }
}
