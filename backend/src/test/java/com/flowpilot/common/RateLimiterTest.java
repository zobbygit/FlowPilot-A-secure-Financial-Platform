package com.flowpilot.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimiterTest {
    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToTheLimitThenReturns429WithRetryAfter() {
        for (int i = 0; i < 3; i++) limiter.check("login", "1.2.3.4", 3, Duration.ofMinutes(10));
        ApiException ex = assertThrows(ApiException.class, () -> limiter.check("login", "1.2.3.4", 3, Duration.ofMinutes(10)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status());
        assertEquals("RATE_LIMITED", ex.code());
        assertEquals(600L, ex.retryAfterSeconds());
    }

    @Test
    void keysAndBucketsAreIndependent() {
        limiter.check("login", "a", 1, Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.check("login", "b", 1, Duration.ofMinutes(1)));
        assertDoesNotThrow(() -> limiter.check("register", "a", 1, Duration.ofMinutes(1)));
    }
}
