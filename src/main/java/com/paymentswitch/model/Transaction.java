package com.paymentswitch.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A transaction as persisted by the switch. The full card number (PAN) is
 * never stored: only the masked form, the BIN (first six digits, needed for
 * routing) and nothing else.
 */
@Entity
@Table(name = "transactions",
        indexes = @Index(name = "idx_transactions_merchant", columnList = "merchant_id"))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "masked_card_number", nullable = false, length = 32)
    private String maskedCardNumber;

    @Column(name = "card_bin", nullable = false, length = 6)
    private String cardBin;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "acquirer_id", nullable = false, length = 64)
    private String acquirerId;

    @Column(name = "issuer_id", length = 64)
    private String issuerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    /** ISO 8583-style response code, e.g. 00 = approved, 05 = do not honor. */
    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: concurrent updates to the same row fail instead of silently overwriting. */
    @Version
    private long version;

    protected Transaction() {
        // for JPA
    }

    public Transaction(String idempotencyKey, String maskedCardNumber, String cardBin, String merchantId,
                       String acquirerId, BigDecimal amount, String currency, Channel channel) {
        this.idempotencyKey = idempotencyKey;
        this.maskedCardNumber = maskedCardNumber;
        this.cardBin = cardBin;
        this.merchantId = merchantId;
        this.acquirerId = acquirerId;
        this.amount = amount;
        this.currency = currency;
        this.channel = channel;
        this.status = TransactionStatus.RECEIVED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markValidated() {
        transition(TransactionStatus.RECEIVED, TransactionStatus.VALIDATED);
    }

    public void markRouted(String issuerId) {
        transition(TransactionStatus.VALIDATED, TransactionStatus.ROUTED);
        this.issuerId = issuerId;
    }

    public void approve(String responseCode) {
        transition(TransactionStatus.ROUTED, TransactionStatus.APPROVED);
        this.responseCode = responseCode;
    }

    public void decline(String responseCode, String reason) {
        if (status.isFinal()) {
            throw new IllegalStateException("Transaction " + id + " is already " + status);
        }
        this.status = TransactionStatus.DECLINED;
        this.responseCode = responseCode;
        this.declineReason = reason;
    }

    private void transition(TransactionStatus expected, TransactionStatus next) {
        if (status != expected) {
            throw new IllegalStateException("Cannot move transaction " + id + " from " + status + " to " + next);
        }
        this.status = next;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMaskedCardNumber() { return maskedCardNumber; }
    public String getCardBin() { return cardBin; }
    public String getMerchantId() { return merchantId; }
    public String getAcquirerId() { return acquirerId; }
    public String getIssuerId() { return issuerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Channel getChannel() { return channel; }
    public TransactionStatus getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getDeclineReason() { return declineReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
