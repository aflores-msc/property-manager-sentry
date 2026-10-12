package com.propertymanager.sentry.triage;

/**
 * Urgency of a ticket. Declared from least to most severe, so the natural enum order ({@link
 * #compareTo}) is the severity order. That's what makes the table sort logically instead of
 * alphabetically.
 */
public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
