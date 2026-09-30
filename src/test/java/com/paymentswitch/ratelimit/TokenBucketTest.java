package com.paymentswitch.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    private final AtomicLong clock = new AtomicLong();

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucket bucket = new TokenBucket(3, 1, clock::get);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsOverTimeButNeverAboveCapacity() {
        TokenBucket bucket = new TokenBucket(2, 2, clock::get); // 2 tokens / second
        bucket.tryConsume();
        bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        clock.addAndGet(500_000_000L); // 0.5s -> 1 token
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        clock.addAndGet(60_000_000_000L); // a minute idle still caps at 2
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void reportsWaitUntilNextToken() {
        TokenBucket bucket = new TokenBucket(1, 0.5, clock::get); // one token every 2s
        assertThat(bucket.secondsUntilNextToken()).isZero();
        bucket.tryConsume();
        assertThat(bucket.secondsUntilNextToken()).isEqualTo(2);
    }
}
