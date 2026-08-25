package com.smoke.security;

import com.smoke.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例登录失败限流。集群部署时应由网关或 Redis 限流替代。
 */
@Service
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Value("${app.login-rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.login-rate-limit.max-failures:5}")
    private int maxFailures;

    @Value("${app.login-rate-limit.window-seconds:900}")
    private long windowSeconds;

    @Value("${app.login-rate-limit.lock-seconds:900}")
    private long lockSeconds;

    public void check(String clientAddress) {
        if (!enabled) {
            return;
        }
        Attempt attempt = attempts.get(clientAddress);
        if (attempt == null) {
            return;
        }
        synchronized (attempt) {
            Instant now = Instant.now();
            if (attempt.lockedUntil != null && attempt.lockedUntil.isAfter(now)) {
                throw new BusinessException(429, "登录尝试过于频繁，请稍后再试");
            }
            if (Duration.between(attempt.windowStartedAt, now).getSeconds() > windowSeconds) {
                attempts.remove(clientAddress, attempt);
            }
        }
    }

    public void recordFailure(String clientAddress) {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        attempts.compute(clientAddress, (key, current) -> {
            if (current == null || Duration.between(current.windowStartedAt, now).getSeconds() > windowSeconds) {
                current = new Attempt(now);
            }
            synchronized (current) {
                current.failures++;
                if (current.failures >= maxFailures) {
                    current.lockedUntil = now.plusSeconds(lockSeconds);
                }
            }
            return current;
        });
    }

    public void recordSuccess(String clientAddress) {
        attempts.remove(clientAddress);
    }

    private static final class Attempt {
        private final Instant windowStartedAt;
        private int failures;
        private Instant lockedUntil;

        private Attempt(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
