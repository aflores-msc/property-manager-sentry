package com.propertymanager.sentry.ui;

import java.util.function.Consumer;

import atlantafx.base.theme.Styles;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Shown while the app waits for the user to approve Gmail access in the browser: explains what's
 * going on, offers the link in case no browser opened, and lets the user cancel the wait.
 * Closed by the main window as soon as authorization finishes.
 */
final class AuthorizationDialog {

    private static final ButtonType OPEN = new ButtonType("Open browser", ButtonBar.ButtonData.LEFT);
    private static final ButtonType COPY = new ButtonType("Copy link", ButtonBar.ButtonData.LEFT);

    private final Alert alert = new Alert(Alert.AlertType.INFORMATION);
    private boolean closedByApp;

    AuthorizationDialog(Window owner, String url, Consumer<String> openUrl, Runnable cancel) {
        alert.initOwner(owner);
        alert.initModality(Modality.NONE);
        alert.setTitle("Connect Gmail");
        alert.setHeaderText("Allow access to Gmail in your browser");

        var explanation = new Label("""
                Sign in with your Gmail account and allow access. When the browser says you can close \
                the window, the sync continues here automatically.

                No browser window? Open this link yourself:""");
        explanation.setWrapText(true);
        var link = new TextField(url);
        link.setEditable(false);
        var hint = new Label("Waiting gives up after 5 minutes.");
        hint.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        var content = new VBox(10, explanation, link, hint);
        content.setPrefWidth(520);
        alert.getDialogPane().setContent(content);

        alert.getButtonTypes().setAll(OPEN, COPY, ButtonType.CANCEL);
        // Open and Copy must keep the dialog open, so their action events are consumed.
        alert.getDialogPane().lookupButton(OPEN).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            openUrl.accept(url);
        });
        alert.getDialogPane().lookupButton(COPY).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            var clipboard = new ClipboardContent();
            clipboard.putString(url);
            Clipboard.getSystemClipboard().setContent(clipboard);
        });
        // Cancel button, Esc and the window's close box all end with CANCEL as the result. JavaFX also
        // sets CANCEL when the app closes the dialog itself, which must not cancel anything.
        alert.resultProperty().addListener((_, _, result) -> {
            if (result == ButtonType.CANCEL && !closedByApp) {
                cancel.run();
            }
        });
    }

    void show() {
        alert.show();
    }

    void close() {
        closedByApp = true;
        alert.close();
    }
}
