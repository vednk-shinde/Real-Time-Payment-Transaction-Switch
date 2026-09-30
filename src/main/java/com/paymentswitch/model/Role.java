package com.paymentswitch.model;

public enum Role {
    ADMIN,
    OPERATOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
