package com.paymentswitch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Typed view of the {@code app.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Kafka kafka,
        Jwt jwt,
        RateLimit rateLimit,
        Risk risk,
        @DefaultValue("true") boolean seedDemoUsers) {

    public record Kafka(
            @DefaultValue("transaction-events") String topic,
            @DefaultValue("3") int partitions) {
    }

    public record Jwt(
            String secret,
            @DefaultValue("60") long expirationMinutes) {
    }

    /** Token bucket per client: {@code capacity} burst, refilled at {@code refillPerSecond}. */
    public record RateLimit(
            @DefaultValue("20") int capacity,
            @DefaultValue("10") double refillPerSecond) {
    }

    public record Risk(
            @DefaultValue("10000.00") BigDecimal maxAmount) {
    }
}
