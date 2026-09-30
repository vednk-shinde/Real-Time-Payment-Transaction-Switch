package com.paymentswitch.ratelimit;

import java.util.function.LongSupplier;

/**
 * Classic token bucket: holds up to {@code capacity} tokens, refilled
 * continuously at {@code refillPerSecond}. Each request takes one token;
 * an empty bucket means the caller is over its rate. Allows short bursts
 * up to capacity while enforcing the long-run average rate.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefill;

    public TokenBucket(int capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    TokenBucket(int capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** Seconds until one token is available (0 if one is available now). */
    public synchronized long secondsUntilNextToken() {
        refill();
        if (tokens >= 1) return 0;
        return (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000_000d);
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
    }
}
