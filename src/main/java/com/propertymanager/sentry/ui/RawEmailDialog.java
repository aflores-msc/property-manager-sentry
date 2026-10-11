package com.propertymanager.sentry.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Read-only, non-modal window with the email exactly as it was sent to the AI. */
final class RawEmailDialog extends Dialog<Void> {

    RawEmailDialog(Window owner, String subject, String body) {
        initOwner(owner);
        initModality(Modality.NONE);
        setTitle("Original email — " + subject);
        setResizable(true);

        var text = new TextArea(body.isBlank() ? "(empty body)" : body);
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefSize(720, 520);
        text.getStyleClass().add("raw-email");

        getDialogPane().setContent(text);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
