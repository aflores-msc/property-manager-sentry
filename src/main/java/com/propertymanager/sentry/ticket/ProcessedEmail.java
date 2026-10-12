package com.propertymanager.sentry.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

/** Marker that an email went through triage to completion, so it is never re-classified. */
@Entity
public class ProcessedEmail {

    public enum Outcome {
        TICKETED,
        IGNORED
    }

    @Id
    @Column(length = 128)
    private String emailId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Outcome outcome;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEmail() {
        // for JPA
    }

    public ProcessedEmail(String emailId, Outcome outcome, Instant processedAt) {
        this.emailId = emailId;
        this.outcome = outcome;
        this.processedAt = processedAt;
    }

    public String getEmailId() {
        return emailId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
