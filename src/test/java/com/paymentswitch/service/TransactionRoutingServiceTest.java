package com.paymentswitch.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRoutingServiceTest {

    private final TransactionRoutingService routing = new TransactionRoutingService();

    @ParameterizedTest
    @CsvSource({
            "411111, ISSUER-VISA-SIM",
            "555555, ISSUER-MASTERCARD-SIM",
            "510000, ISSUER-MASTERCARD-SIM",
            "222300, ISSUER-MASTERCARD-SIM",
            "272099, ISSUER-MASTERCARD-SIM",
            "378282, ISSUER-AMEX-SIM",
            "340000, ISSUER-AMEX-SIM",
            "601111, ISSUER-DISCOVER-SIM",
            "650000, ISSUER-DISCOVER-SIM"
    })
    void routesKnownBinRanges(String bin, String expectedIssuer) {
        assertThat(routing.resolveIssuer(bin)).contains(expectedIssuer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "900000", "272100", "560000", "abc123", "12"})
    void unknownOrMalformedBinsHaveNoRoute(String bin) {
        assertThat(routing.resolveIssuer(bin)).isEmpty();
    }
}
