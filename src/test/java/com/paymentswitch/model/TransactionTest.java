package com.paymentswitch.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static Transaction newTx() {
        return new Transaction("k", "****1111", "411111", "M", "A", BigDecimal.TEN, "USD", Channel.POS);
    }

    @Test
    void followsTheHappyPathStateMachine() {
        Transaction tx = newTx();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECEIVED);
        tx.markValidated();
        tx.markRouted("ISSUER");
        tx.approve("00");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(tx.getIssuerId()).isEqualTo("ISSUER");
    }

    @Test
    void rejectsOutOfOrderTransitions() {
        Transaction tx = newTx();
        assertThatThrownBy(() -> tx.markRouted("ISSUER")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.approve("00")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalStatesCannotChange() {
        Transaction tx = newTx();
        tx.decline("14", "bad card");
        assertThatThrownBy(() -> tx.decline("05", "again")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tx::markValidated).isInstanceOf(IllegalStateException.class);
    }
}
