package com.paymentswitch.exception;

import java.util.UUID;

/** Mapped to 404 Not Found. */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID id) {
        super("Transaction " + id + " not found");
    }
}
