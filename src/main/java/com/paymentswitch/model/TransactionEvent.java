package com.paymentswitch.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of one state transition, published to Kafka. The stream
 * of these for a transaction id is its complete, replayable audit trail.
 * Carries only the masked card number, never the PAN.
 */
public record TransactionEvent(
        UUID eventId,
        UUID transactionId,
        String idempotencyKey,
        TransactionStatus previousStatus,
        TransactionStatus status,
        String maskedCardNumber,
        String merchantId,
        String issuerId,
        BigDecimal amount,
        String currency,
        String responseCode,
        String reason,
        Instant occurredAt) {

    public static TransactionEvent of(Transaction tx, TransactionStatus previousStatus) {
        return new TransactionEvent(
                UUID.randomUUID(),
                tx.getId(),
                tx.getIdempotencyKey(),
                previousStatus,
                tx.getStatus(),
                tx.getMaskedCardNumber(),
                tx.getMerchantId(),
                tx.getIssuerId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getResponseCode(),
                tx.getDeclineReason(),
                Instant.now());
    }
}
