package com.paymentswitch.ratelimit;

import com.paymentswitch.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link TokenBucket} per client key. In-memory and per-instance: a
 * multi-node deployment would move the buckets to Redis so all nodes
 * share one budget per client.
 */
@Component
public class RateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double refillPerSecond;

    public RateLimiter(AppProperties props) {
        this.capacity = props.rateLimit().capacity();
        this.refillPerSecond = props.rateLimit().refillPerSecond();
    }

    public Decision tryAcquire(String clientKey) {
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, k -> new TokenBucket(capacity, refillPerSecond));
        if (bucket.tryConsume()) {
            return Decision.ALLOWED;
        }
        return new Decision(false, Math.max(1, bucket.secondsUntilNextToken()));
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }
}
