package com.paymentswitch.exception;

/** Thrown when a request reuses an idempotency key; mapped to 409 Conflict. */
public class DuplicateTransactionException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateTransactionException(String idempotencyKey) {
        super("A transaction with idempotencyKey '" + idempotencyKey + "' has already been processed");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
