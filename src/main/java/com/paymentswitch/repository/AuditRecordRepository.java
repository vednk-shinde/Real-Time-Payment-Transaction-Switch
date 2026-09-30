package com.paymentswitch.repository;

import com.paymentswitch.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    boolean existsByEventId(UUID eventId);

    List<AuditRecord> findByTransactionIdOrderByOccurredAtAscIdAsc(UUID transactionId);
}
