package com.flowpilot.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, in-process rate limiter (no Redis by project rule).
 * Limitation: counters are per backend instance. With N instances the effective limit is N x max;
 * see docs/SECURITY.md for the DB-backed upgrade path.
 */
@Component
public class RateLimiter {
    private final Map<String, Cache<String, AtomicInteger>> buckets = new ConcurrentHashMap<>();

    public void check(String bucket, String key, int max, Duration window) {
        Cache<String, AtomicInteger> cache = buckets.computeIfAbsent(bucket + "|" + window,
                k -> Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build());
        int used = cache.get(key == null ? "unknown" : key, k -> new AtomicInteger()).incrementAndGet();
        if (used > max) throw ApiException.tooManyRequests(window.toSeconds());
    }
}
