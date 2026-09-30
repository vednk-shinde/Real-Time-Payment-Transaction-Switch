package com.paymentswitch.model;

/**
 * Lifecycle of a transaction through the switch:
 * {@code RECEIVED -> VALIDATED -> ROUTED -> APPROVED | DECLINED}.
 * A transaction can also go straight to DECLINED from RECEIVED (failed
 * validation) or VALIDATED (no route to an issuer).
 */
public enum TransactionStatus {
    RECEIVED,
    VALIDATED,
    ROUTED,
    APPROVED,
    DECLINED;

    public boolean isFinal() {
        return this == APPROVED || this == DECLINED;
    }
}
