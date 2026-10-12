package com.propertymanager.sentry.ticket;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, String> {

    @Query("select p.emailId from ProcessedEmail p where p.emailId in :ids")
    Set<String> findKnownIds(Collection<String> ids);
}
