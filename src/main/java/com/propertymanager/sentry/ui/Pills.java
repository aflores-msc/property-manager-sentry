package com.propertymanager.sentry.ui;

import com.propertymanager.sentry.ticket.TicketStatus;
import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.Priority;
import javafx.scene.control.Label;

/** Small colored labels for category, priority and status (styled in {@code sentry.css}). */
final class Pills {

    private Pills() {}

    static Label of(Category category) {
        return pill(category.label(), "category-" + cssName(category));
    }

    static Label of(Priority priority) {
        return pill(priority.label(), "priority-" + cssName(priority));
    }

    static Label of(TicketStatus status) {
        return pill(status.label(), "status-" + cssName(status));
    }

    private static Label pill(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().addAll("pill", styleClass);
        return label;
    }

    private static String cssName(Enum<?> value) {
        return value.name().toLowerCase().replace('_', '-');
    }
}
