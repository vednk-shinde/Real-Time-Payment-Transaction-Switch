package com.paymentswitch.dto;

import com.paymentswitch.model.Channel;
import com.paymentswitch.model.Transaction;
import com.paymentswitch.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String idempotencyKey,
        String maskedCardNumber,
        String merchantId,
        String acquirerId,
        String issuerId,
        BigDecimal amount,
        String currency,
        Channel channel,
        TransactionStatus status,
        String responseCode,
        String declineReason,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getIdempotencyKey(),
                tx.getMaskedCardNumber(),
                tx.getMerchantId(),
                tx.getAcquirerId(),
                tx.getIssuerId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getChannel(),
                tx.getStatus(),
                tx.getResponseCode(),
                tx.getDeclineReason(),
                tx.getCreatedAt(),
                tx.getUpdatedAt());
    }
}
