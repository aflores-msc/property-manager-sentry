package com.propertymanager.sentry.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import com.propertymanager.sentry.ticket.TicketFields;
import com.propertymanager.sentry.ticket.TicketStatus;
import com.propertymanager.sentry.ticket.TicketSummary;
import com.propertymanager.sentry.triage.CityNoticeDetails;
import com.propertymanager.sentry.triage.DisputeDetails;
import com.propertymanager.sentry.triage.MaintenanceDetails;
import com.propertymanager.sentry.triage.TicketDetails;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Right-hand panel: everything known about the selected ticket. */
final class TicketDetailPane extends ScrollPane {

    private static final DateTimeFormatter RECEIVED =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final VBox content = new VBox(14);
    private final BiConsumer<TicketRow, TicketStatus> onStatusChange;
    private final Consumer<TicketRow> onViewOriginal;

    TicketDetailPane(
            BiConsumer<TicketRow, TicketStatus> onStatusChange,
            Consumer<TicketRow> onViewOriginal) {
        this.onStatusChange = onStatusChange;
        this.onViewOriginal = onViewOriginal;
        content.setPadding(new Insets(20));
        content.getStyleClass().add("detail-pane");
        setContent(content);
        setFitToWidth(true);
        show(null);
    }

    void show(TicketRow row) {
        content.getChildren().clear();
        if (row == null) {
            var empty = new Label("Select a ticket to see its details.");
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            content.getChildren().add(empty);
            return;
        }
        TicketSummary ticket = row.ticket();

        var subject = new Label(ticket.subject());
        subject.getStyleClass().add(Styles.TITLE_3);
        subject.setWrapText(true);

        var meta = new Label(ticket.sender() + "  ·  " + RECEIVED.format(ticket.receivedAt()));
        meta.getStyleClass().add(Styles.TEXT_MUTED);
        meta.setWrapText(true);

        var pills = new FlowPane(8, 8, Pills.of(ticket.category()), Pills.of(ticket.priority()));

        content.getChildren().addAll(subject, meta, pills);
        callout(ticket.details()).ifPresent(content.getChildren()::add);

        if (ticket.details() != null && !ticket.details().summary().isBlank()) {
            content.getChildren().add(paragraph(ticket.details().summary()));
        }

        content.getChildren()
                .addAll(
                        new Separator(),
                        sectionTitle("Property"),
                        grid(
                                row("Address", ticket.propertyAddress()),
                                row("Unit", ticket.unitNumber()),
                                row("Sender type", ticket.senderType())));

        content.getChildren()
                .addAll(
                        sectionTitle(ticket.category().label() + " details"),
                        grid(
                                TicketFields.of(ticket.details()).entrySet().stream()
                                        .map(field -> row(field.getKey(), field.getValue()))
                                        .toArray(Node[][]::new)));

        if (ticket.rationale() != null && !ticket.rationale().isBlank()) {
            var why = paragraph("Why: " + ticket.rationale());
            why.getStyleClass().add(Styles.TEXT_MUTED);
            content.getChildren().add(why);
        }

        content.getChildren().addAll(new Separator(), actions(row));
    }

    /** Highlights that deserve attention, chosen by pattern matching on the sealed details type. */
    private static Optional<Node> callout(TicketDetails details) {
        Message message =
                switch (details) {
                    case MaintenanceDetails m when m.safetyRisk() ->
                            new Message(
                                    "Safety risk",
                                    "The report suggests a risk to people or property. Act"
                                            + " quickly.");
                    case DisputeDetails d when d.escalationNeeded() ->
                            new Message(
                                    "Escalation recommended",
                                    "Management or legal involvement may be needed.");
                    case CityNoticeDetails c when !c.deadline().isBlank() ->
                            new Message("Deadline " + c.deadline(), c.requiredAction());
                    case null, default -> null;
                };
        if (message == null) {
            return Optional.empty();
        }
        message.getStyleClass()
                .add(details instanceof CityNoticeDetails ? Styles.WARNING : Styles.DANGER);
        return Optional.of(message);
    }

    private Node actions(TicketRow row) {
        var status = new ComboBox<TicketStatus>();
        status.getItems().setAll(TicketStatus.values());
        status.setValue(row.statusProperty().get());
        status.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(TicketStatus value) {
                        return value == null ? "" : value.label();
                    }

                    @Override
                    public TicketStatus fromString(String text) {
                        throw new UnsupportedOperationException();
                    }
                });
        status.valueProperty()
                .addListener(
                        (_, old, now) -> {
                            if (now != null && now != old) {
                                onStatusChange.accept(row, now);
                            }
                        });

        var viewOriginal = new Button("View original email");
        viewOriginal.getStyleClass().add(Styles.BUTTON_OUTLINED);
        viewOriginal.setOnAction(_ -> onViewOriginal.accept(row));

        var statusLabel = new Label("Status");
        var box = new HBox(10, statusLabel, status, new Spacer(), viewOriginal);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label sectionTitle(String text) {
        var label = new Label(text);
        label.getStyleClass().addAll(Styles.TITLE_4);
        return label;
    }

    private static Label paragraph(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static Node[] row(String name, String value) {
        var key = new Label(name);
        key.getStyleClass().add(Styles.TEXT_MUTED);
        var val = new Label(value == null || value.isBlank() ? "—" : value);
        val.setWrapText(true);
        return new Node[] {key, val};
    }

    private static GridPane grid(Node[]... rows) {
        var grid = new GridPane(16, 6);
        var keys = new ColumnConstraints();
        keys.setMinWidth(Region.USE_PREF_SIZE); // never truncate field names
        var values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, values);
        for (int i = 0; i < rows.length; i++) {
            grid.addRow(i, rows[i]);
        }
        return grid;
    }
}
