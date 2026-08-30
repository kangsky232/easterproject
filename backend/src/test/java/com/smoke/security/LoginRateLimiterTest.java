package com.smoke.security;

import com.smoke.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    @Test
    void doesNotLockClientWhenLimiterIsDisabled() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        ReflectionTestUtils.setField(limiter, "enabled", false);

        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.recordFailure("127.0.0.1");
        }

        assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    }

    @Test
    void locksClientAfterConfiguredNumberOfFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        ReflectionTestUtils.setField(limiter, "enabled", true);
        ReflectionTestUtils.setField(limiter, "maxFailures", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 900L);
        ReflectionTestUtils.setField(limiter, "lockSeconds", 900L);

        limiter.recordFailure("127.0.0.1");
        limiter.recordFailure("127.0.0.1");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter.check("127.0.0.1"));
        assertEquals(429, exception.getCode());
    }
}
