package com.paymentswitch.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "5555555555554444", "378282246310005", "6011111111111117", "2223003122003222"})
    void acceptsValidTestCardNumbers(String pan) {
        assertThat(LuhnValidator.isValid(pan)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111112", "1234567812345678", "41111111111a1111"})
    void rejectsInvalidNumbers(String pan) {
        assertThat(LuhnValidator.isValid(pan)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingInput(String pan) {
        assertThat(LuhnValidator.isValid(pan)).isFalse();
    }
}
