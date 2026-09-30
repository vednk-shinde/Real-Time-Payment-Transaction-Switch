package com.paymentswitch.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardMaskingServiceTest {

    private final CardMaskingService masking = new CardMaskingService();

    @Test
    void masksAllButLastFourDigits() {
        assertThat(masking.mask("4111111111111111")).isEqualTo("****1111");
        assertThat(masking.mask("378282246310005")).isEqualTo("****0005");
    }

    @Test
    void neverEchoesShortInput() {
        assertThat(masking.mask("123")).isEqualTo("****");
        assertThat(masking.mask(null)).isEqualTo("****");
    }

    @Test
    void extractsSixDigitBin() {
        assertThat(masking.bin("4111111111111111")).isEqualTo("411111");
        assertThatThrownBy(() -> masking.bin("41111")).isInstanceOf(IllegalArgumentException.class);
    }
}
