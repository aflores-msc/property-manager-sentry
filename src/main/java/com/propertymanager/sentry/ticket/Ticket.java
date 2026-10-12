package com.propertymanager.sentry.ticket;

import com.propertymanager.sentry.triage.Category;
import com.propertymanager.sentry.triage.Priority;
import com.propertymanager.sentry.triage.TicketDetails;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

/** A property-management email turned into a piece of work. */
@Entity
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String emailId;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(nullable = false, length = 1024)
    private String sender;

    @Column(nullable = false, length = 2048)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(nullable = false)
    private boolean unread = true;

    @Column(length = 128)
    private String senderType;

    @Column(length = 1024)
    private String propertyAddress;

    @Column(length = 128)
    private String unitNumber;

    @Column(length = 4096)
    private String rationale;

    @Lob
    @Convert(converter = TicketDetailsConverter.class)
    private TicketDetails details;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String rawBody;

    protected Ticket() {
        // for JPA
    }

    public Ticket(
            String emailId,
            Instant receivedAt,
            Instant processedAt,
            String sender,
            String subject,
            Category category,
            Priority priority,
            String senderType,
            String propertyAddress,
            String unitNumber,
            String rationale,
            TicketDetails details,
            String rawBody) {
        this.emailId = emailId;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
        this.sender = sender;
        this.subject = subject;
        this.category = category;
        this.priority = priority;
        this.senderType = senderType;
        this.propertyAddress = propertyAddress;
        this.unitNumber = unitNumber;
        this.rationale = rationale;
        this.details = details;
        this.rawBody = rawBody;
    }

    public Long getId() {
        return id;
    }

    public String getEmailId() {
        return emailId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public Category getCategory() {
        return category;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public String getSenderType() {
        return senderType;
    }

    public String getPropertyAddress() {
        return propertyAddress;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public String getRationale() {
        return rationale;
    }

    public TicketDetails getDetails() {
        return details;
    }

    public String getRawBody() {
        return rawBody;
    }
}
