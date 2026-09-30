package com.paymentswitch.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Downstream projection of the Kafka event stream: one row per consumed
 * {@link TransactionEvent}. The unique {@code eventId} makes consumption
 * idempotent under Kafka's at-least-once redelivery.
 */
@Entity
@Table(name = "transaction_audit",
        indexes = @Index(name = "idx_audit_transaction", columnList = "transaction_id"))
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 16)
    private TransactionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    protected AuditRecord() {
        // for JPA
    }

    public AuditRecord(TransactionEvent event, int kafkaPartition, long kafkaOffset) {
        this.eventId = event.eventId();
        this.transactionId = event.transactionId();
        this.previousStatus = event.previousStatus();
        this.status = event.status();
        this.responseCode = event.responseCode();
        this.reason = event.reason();
        this.occurredAt = event.occurredAt();
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getTransactionId() { return transactionId; }
    public TransactionStatus getPreviousStatus() { return previousStatus; }
    public TransactionStatus getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getKafkaPartition() { return kafkaPartition; }
    public long getKafkaOffset() { return kafkaOffset; }
}
