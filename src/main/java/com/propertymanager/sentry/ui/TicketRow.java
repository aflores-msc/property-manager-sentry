package com.propertymanager.sentry.ui;

import com.propertymanager.sentry.ticket.TicketStatus;
import com.propertymanager.sentry.ticket.TicketSummary;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Table row view model: the immutable ticket data plus the two things the user can change in
 * place, as JavaFX properties. The table observes these properties (see {@link #observables()}),
 * so marking a ticket read or changing its status updates the row without replacing it.
 */
final class TicketRow {

    private final TicketSummary ticket;
    private final BooleanProperty unread;
    private final ObjectProperty<TicketStatus> status;

    TicketRow(TicketSummary ticket) {
        this.ticket = ticket;
        this.unread = new SimpleBooleanProperty(ticket.unread());
        this.status = new SimpleObjectProperty<>(ticket.status());
    }

    TicketSummary ticket() {
        return ticket;
    }

    long id() {
        return ticket.id();
    }

    BooleanProperty unreadProperty() {
        return unread;
    }

    ObjectProperty<TicketStatus> statusProperty() {
        return status;
    }

    /** The ticket as it is now, including in-place edits. */
    TicketSummary snapshot() {
        return ticket.withStatus(status.get());
    }

    /** For {@code FXCollections.observableArrayList(extractor)}: changes to these fire list updates. */
    Observable[] observables() {
        return new Observable[] {unread, status};
    }
}
