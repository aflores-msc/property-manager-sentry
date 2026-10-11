package com.propertymanager.sentry.ticket;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            select new com.propertymanager.sentry.ticket.TicketSummary(
                t.id, t.emailId, t.receivedAt, t.sender, t.subject, t.category, t.priority, t.status,
                t.unread, t.senderType, t.propertyAddress, t.unitNumber, t.rationale, t.details)
            from Ticket t
            order by t.receivedAt desc""")
    List<TicketSummary> findAllSummaries();

    @Query("select t.rawBody from Ticket t where t.id = :id")
    Optional<String> findRawBody(long id);

    @Modifying
    @Query("update Ticket t set t.status = :status where t.id = :id")
    int updateStatus(long id, TicketStatus status);

    @Modifying
    @Query("update Ticket t set t.unread = false where t.id = :id and t.unread = true")
    int markRead(long id);
}
