package com.acme.schedulemanager.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginAttemptRateLimiter {
    private final StringRedisTemplate redis;

    public LoginAttemptRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String key, int max, Duration ttl) {
        String redisKey = "ratelimit:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redis.expire(redisKey, ttl);
        }
        if (count != null && count > max) {
            throw new IllegalArgumentException("요청 횟수가 초과되었습니다. 잠시 후 다시 시도하세요.");
        }
    }
}
