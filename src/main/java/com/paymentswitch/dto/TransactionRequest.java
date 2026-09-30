package com.paymentswitch.dto;

import com.paymentswitch.model.Channel;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound transaction. Bean validation rejects malformed requests with 400
 * before they reach the switch; business rules (Luhn check, routing, risk)
 * are applied later and produce a DECLINED transaction instead.
 */
public record TransactionRequest(
        @NotBlank @Size(max = 64) String idempotencyKey,
        @NotBlank @Pattern(regexp = "\\d{12,19}", message = "must be 12-19 digits") String cardNumber,
        @NotBlank @Size(max = 64) String merchantId,
        @NotBlank @Size(max = 64) String acquirerId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code, e.g. USD") String currency,
        @NotNull Channel channel) {

    /** Never let the PAN leak into logs via the default record toString. */
    @Override
    public String toString() {
        return "TransactionRequest[idempotencyKey=" + idempotencyKey + ", merchantId=" + merchantId
                + ", amount=" + amount + " " + currency + ", channel=" + channel + "]";
    }
}
