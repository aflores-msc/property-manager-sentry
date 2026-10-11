package com.propertymanager.sentry.ui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.propertymanager.sentry.config.UserPreferences;
import com.propertymanager.sentry.inbox.AutoSyncScheduler;
import com.propertymanager.sentry.inbox.InboxSyncService;
import com.propertymanager.sentry.inbox.SyncEvent;
import com.propertymanager.sentry.inbox.SyncTrigger;
import com.propertymanager.sentry.ticket.TicketCsvExporter;
import com.propertymanager.sentry.ticket.TicketService;
import com.propertymanager.sentry.ticket.TicketStatus;
import com.propertymanager.sentry.ticket.TicketSummary;
import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.TriageOutcome;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * The dashboard: a sortable, filterable ticket table, a detail panel and a status bar.
 *
 * <p>Not a singleton bean: {@code SentryFxApplication} builds it with Spring's
 * {@code createBean} (so the constructor is still autowired) only once JavaFX is running. That
 * keeps the Spring context free of UI and lets tests start it without a display. Sync events
 * arrive on background threads, so {@link #onSyncEvent} hops to the JavaFX thread with
 * {@link Platform#runLater}.
 */
public class MainWindow {

    private static final DateTimeFormatter RECEIVED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final PseudoClass UNREAD = PseudoClass.getPseudoClass("unread");

    private final TicketService tickets;
    private final InboxSyncService syncService;
    private final AutoSyncScheduler autoSync;
    private final TicketCsvExporter csvExporter;
    private final UserPreferences preferences;

    // The extractor makes the list fire "updated" events when a row's unread/status property changes.
    private final ObservableList<TicketRow> allTickets = FXCollections.observableArrayList(TicketRow::observables);
    private final FilteredList<TicketRow> visibleTickets = new FilteredList<>(allTickets);
    private final TableView<TicketRow> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<Category> categoryFilter = new ComboBox<>();
    private final Button syncButton = new Button("Sync inbox");
    private final ProgressBar progress = new ProgressBar();
    private final Label statusText = new Label("Ready");
    private final Label countsText = new Label();
    private final TicketDetailPane detailPane = new TicketDetailPane(this::changeStatus, this::showOriginal);
    private Stage stage;

    public MainWindow(TicketService tickets, InboxSyncService syncService, AutoSyncScheduler autoSync,
                      TicketCsvExporter csvExporter, UserPreferences preferences) {
        this.tickets = tickets;
        this.syncService = syncService;
        this.autoSync = autoSync;
        this.csvExporter = csvExporter;
        this.preferences = preferences;
    }

    public void show(Stage stage) {
        this.stage = stage;
        applyTheme(preferences.darkTheme());

        var root = new BorderPane(body(), header(), null, statusBar(), null);
        // Default to a roomy window, but never larger than the screen it opens on.
        var screen = Screen.getPrimary().getVisualBounds();
        var scene = new Scene(root, Math.min(1440, screen.getWidth() * 0.95), Math.min(880, screen.getHeight() * 0.95));
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/ui/sentry.css")).toExternalForm());

        stage.setTitle("Property Manager Sentry");
        stage.setMinWidth(980);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
        reloadTickets();
    }

    // ---- layout ---------------------------------------------------------------------------

    private HBox header() {
        var title = new Label("Property Manager Sentry");
        title.getStyleClass().add(Styles.TITLE_3);
        var subtitle = new Label("AI triage for your property inbox");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        var titles = new VBox(2, title, subtitle);

        search.setPromptText("Search subject, sender, address…");
        search.setPrefWidth(280);
        search.textProperty().addListener((_, _, _) -> applyFilter());

        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(Category.MAINTENANCE, Category.CITY_NOTICE, Category.TENANT_DISPUTE);
        categoryFilter.setConverter(labelConverter(category -> category == null ? "All categories" : category.label()));
        categoryFilter.setValue(null);
        categoryFilter.valueProperty().addListener((_, _, _) -> applyFilter());

        syncButton.getStyleClass().add(Styles.ACCENT);
        syncButton.setDefaultButton(true);
        syncButton.setOnAction(_ -> syncService.startSync(SyncTrigger.MANUAL));

        var export = new Button("Export CSV");
        export.setOnAction(_ -> exportCsv());

        var settings = new Button("Settings");
        settings.setOnAction(_ -> openSettings());

        var theme = new ToggleButton("Dark mode");
        theme.setSelected(preferences.darkTheme());
        theme.selectedProperty().addListener((_, _, dark) -> {
            preferences.setDarkTheme(dark);
            applyTheme(dark);
        });

        var header = new HBox(10, titles, new Spacer(), search, categoryFilter, theme, settings, export, syncButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 18, 14, 18));
        header.getStyleClass().add("app-header");
        return header;
    }

    private SplitPane body() {
        configureTable();
        var split = new SplitPane(table, detailPane);
        split.setDividerPositions(0.64);
        return split;
    }

    private HBox statusBar() {
        progress.setPrefWidth(220);
        progress.setVisible(false);
        progress.managedProperty().bind(progress.visibleProperty()); // take no space while hidden
        countsText.getStyleClass().add(Styles.TEXT_MUTED);
        var bar = new HBox(12, statusText, new Spacer(), countsText, progress);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 18, 6, 18));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void configureTable() {
        var received = column("Received", TicketSummary::receivedAt, 135);
        received.setMinWidth(135);
        received.setCellFactory(_ -> textCell(RECEIVED::format));
        received.setSortType(TableColumn.SortType.DESCENDING);

        var subject = column("Subject", TicketSummary::subject, 300);
        var category = column("Category", TicketSummary::category, 130);
        category.setMinWidth(130);
        category.setCellFactory(_ -> graphicCell(Pills::of));
        // Priority is an enum declared LOW..URGENT, so its natural order is the severity order.
        var priority = column("Priority", TicketSummary::priority, 95);
        priority.setMinWidth(95);
        priority.setCellFactory(_ -> graphicCell(Pills::of));
        var status = new TableColumn<TicketRow, TicketStatus>("Status");
        status.setCellValueFactory(cell -> cell.getValue().statusProperty());
        status.setPrefWidth(105);
        status.setMinWidth(105);
        status.setCellFactory(_ -> graphicCell(Pills::of));
        var property = column("Property", ticket -> Objects.requireNonNullElse(ticket.propertyAddress(), ""), 220);

        table.getColumns().setAll(List.of(received, subject, category, priority, status, property));
        table.getSortOrder().add(received);
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No tickets yet. Click \"Sync inbox\" to triage your unread mail."));

        var sorted = new SortedList<>(visibleTickets);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(_ -> new TableRow<>() {
            @Override
            protected void updateItem(TicketRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(UNREAD, !empty && row != null && row.unreadProperty().get());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((_, _, ticket) -> onSelected(ticket));
    }

    // ---- behavior --------------------------------------------------------------------------

    private void onSelected(TicketRow row) {
        detailPane.show(row);
        if (row != null && row.unreadProperty().get()) {
            // Opening a ticket marks it read, in the database too, so it stays read after a restart.
            row.unreadProperty().set(false);
            Fx.background(() -> tickets.markRead(row.id()), this::showError);
            updateCounts();
        }
    }

    private void changeStatus(TicketRow row, TicketStatus status) {
        row.statusProperty().set(status);
        Fx.background(() -> tickets.updateStatus(row.id(), status), this::showError);
        updateCounts();
    }

    private void showOriginal(TicketRow row) {
        Fx.background(() -> tickets.rawBody(row.id()),
                body -> new RawEmailDialog(stage, row.ticket().subject(), body).show(),
                this::showError);
    }

    private void reloadTickets() {
        Fx.background(tickets::findAll, loaded -> {
            Long selectedId = table.getSelectionModel().getSelectedItem() == null
                    ? null : table.getSelectionModel().getSelectedItem().id();
            allTickets.setAll(loaded.stream().map(TicketRow::new).toList());
            if (selectedId != null) {
                allTickets.stream().filter(t -> t.id() == selectedId).findFirst()
                        .ifPresent(t -> table.getSelectionModel().select(t));
            }
            table.sort();
            updateCounts();
        }, this::showError);
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        Category category = categoryFilter.getValue();
        visibleTickets.setPredicate(row ->
                (category == null || row.ticket().category() == category)
                        && (query.isEmpty() || matches(row.ticket(), query)));
        updateCounts();
    }

    private static boolean matches(TicketSummary ticket, String query) {
        return Stream.of(ticket.subject(), ticket.sender(), ticket.propertyAddress(), ticket.unitNumber())
                .filter(Objects::nonNull)
                .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains(query));
    }

    private void updateCounts() {
        long unread = allTickets.stream().filter(row -> row.unreadProperty().get()).count();
        long urgent = allTickets.stream()
                .filter(row -> row.ticket().priority() == Priority.URGENT
                        && row.statusProperty().get() != TicketStatus.RESOLVED)
                .count();
        countsText.setText("%d of %d tickets  ·  %d unread  ·  %d urgent unresolved"
                .formatted(visibleTickets.size(), allTickets.size(), unread, urgent));
    }

    private void exportCsv() {
        if (allTickets.isEmpty()) {
            info("Export CSV", "There are no tickets to export yet.");
            return;
        }
        var chooser = new FileChooser();
        chooser.setTitle("Export tickets");
        chooser.setInitialFileName("tickets.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        var file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        // Exports what is on screen: current filter, sort order and status edits.
        List<TicketSummary> snapshot = table.getItems().stream().map(TicketRow::snapshot).toList();
        Path target = file.toPath();
        Fx.background(() -> {
            csvExporter.export(snapshot, target);
            return snapshot.size();
        }, count -> statusText.setText("Exported %d ticket(s) to %s".formatted(count, target)), this::showError);
    }

    private void openSettings() {
        new SettingsDialog(stage, preferences.autoSyncMinutes()).showAndWait().ifPresent(minutes -> {
            autoSync.reschedule(minutes);
            statusText.setText(minutes == 0 ? "Automatic sync off" : "Automatic sync every %d min".formatted(minutes));
        });
    }

    private static void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    // ---- sync events (background threads) ----------------------------------------------------

    public void onSyncEvent(SyncEvent event) {
        Platform.runLater(() -> handle(event));
    }

    private void handle(SyncEvent event) {
        switch (event) {
            case SyncEvent.Started(var trigger) -> {
                syncButton.setDisable(true);
                progress.setVisible(true);
                progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                statusText.setText(trigger == SyncTrigger.SCHEDULED ? "Automatic sync: checking Gmail…" : "Checking Gmail…");
            }
            case SyncEvent.Planned(var candidates, var known, var toProcess) -> {
                progress.setProgress(toProcess == 0 ? 1 : 0);
                statusText.setText(toProcess == 0
                        ? "No new emails (%d already processed)".formatted(known)
                        : "Triaging %d new email(s)…".formatted(toProcess));
            }
            case SyncEvent.EmailTriaged triaged -> {
                progress.setProgress((double) triaged.completed() / triaged.total());
                statusText.setText("Triaged %d / %d: %s".formatted(triaged.completed(), triaged.total(), describe(triaged.outcome())));
                if (triaged.ticketId() != null) {
                    reloadTickets();
                }
            }
            case SyncEvent.Finished(var summary) -> {
                syncButton.setDisable(false);
                progress.setVisible(false);
                var text = new StringBuilder("Sync done in %ds: %d new ticket(s), %d ignored"
                        .formatted(summary.elapsed().toSeconds(), summary.ticketed(), summary.ignored()));
                if (summary.failed() > 0) {
                    text.append(", %d failed (will retry next sync)".formatted(summary.failed()));
                }
                statusText.setText(text.toString());
                reloadTickets();
            }
            case SyncEvent.Aborted(var message) -> {
                syncButton.setDisable(false);
                progress.setVisible(false);
                statusText.setText("Sync failed");
                error("Could not sync the inbox", message);
            }
        }
    }

    private static String describe(TriageOutcome outcome) {
        return switch (outcome) {
            case TriageOutcome.Ticketed(var routing, _) -> routing.category().label() + " · " + routing.priority().label();
            case TriageOutcome.Ignored _ -> "ignored (not property related)";
            case TriageOutcome.Failed(var reason) -> "failed: " + reason;
        };
    }

    // ---- small helpers -------------------------------------------------------------------------

    private static <T> TableColumn<TicketRow, T> column(String title, Function<TicketSummary, T> value, double width) {
        var column = new TableColumn<TicketRow, T>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue().ticket())));
        column.setPrefWidth(width);
        return column;
    }

    private static <T> TableCell<TicketRow, T> textCell(Function<T, String> format) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
    }

    private static <T> TableCell<TicketRow, T> graphicCell(Function<T, Node> render) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : render.apply(item));
            }
        };
    }

    private static <T> StringConverter<T> labelConverter(Function<T, String> toLabel) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return toLabel.apply(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void showError(Throwable error) {
        error("Something went wrong", String.valueOf(error.getMessage()));
    }

    private void error(String header, String message) {
        var alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(stage);
        alert.setHeaderText(header);
        alert.show();
    }

    private void info(String header, String message) {
        var alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(stage);
        alert.setHeaderText(header);
        alert.show();
    }
}
