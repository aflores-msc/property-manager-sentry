package com.propertymanager.sentry.triage;

/** What kind of email the supervisor decided this is. */
public enum Category {
    MAINTENANCE("Maintenance"),
    CITY_NOTICE("City Notice"),
    TENANT_DISPUTE("Tenant Dispute"),
    /** Not property-management mail (newsletters, system alerts, spam). Never becomes a ticket. */
    IGNORED("Ignored");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
