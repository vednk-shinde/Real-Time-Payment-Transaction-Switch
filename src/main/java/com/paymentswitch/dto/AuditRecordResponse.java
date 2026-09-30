package com.paymentswitch.dto;

import com.paymentswitch.model.AuditRecord;
import com.paymentswitch.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        UUID eventId,
        TransactionStatus previousStatus,
        TransactionStatus status,
        String responseCode,
        String reason,
        Instant occurredAt,
        int kafkaPartition,
        long kafkaOffset) {

    public static AuditRecordResponse from(AuditRecord r) {
        return new AuditRecordResponse(r.getEventId(), r.getPreviousStatus(), r.getStatus(), r.getResponseCode(),
                r.getReason(), r.getOccurredAt(), r.getKafkaPartition(), r.getKafkaOffset());
    }
}
