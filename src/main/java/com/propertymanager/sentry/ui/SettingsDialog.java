package com.propertymanager.sentry.ui;

import atlantafx.base.theme.Styles;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/** Lets the user choose the auto-sync interval. Returns the new value in minutes (0 = off). */
final class SettingsDialog extends Dialog<Integer> {

    SettingsDialog(Window owner, int currentMinutes) {
        initOwner(owner);
        setTitle("Settings");
        setHeaderText("Automatic inbox sync");

        var minutes = new Spinner<Integer>(0, 1440, currentMinutes, 5);
        minutes.setEditable(true);

        var hint = new Label("Minutes between automatic syncs. 0 turns automatic sync off.");
        hint.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        var grid = new GridPane(12, 8);
        grid.addRow(0, new Label("Sync every"), minutes, new Label("min"));
        grid.add(hint, 0, 1, 3, 1);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(button -> button == ButtonType.OK ? minutes.getValue() : null);
    }
}
