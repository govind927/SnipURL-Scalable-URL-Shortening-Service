package com.urlshortener.service;

import com.urlshortener.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based rate limiter using a sliding counter per IP.
 *
 * Strategy: Fixed Window Counter
 *   - Key:   "rate:{ip}"
 *   - Value: request count in current window
 *   - TTL:   window duration (auto-resets after window expires)
 *
 * Example config: max 10 requests per IP per 1 minute.
 *
 * Why Redis and not in-memory?
 *   - Works across multiple app instances (horizontal scaling)
 *   - Survives app restarts
 *   - O(1) operation via Redis INCR + EXPIRE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${app.rate-limit.window-minutes:1}")
    private long windowMinutes;

    private static final String RATE_KEY_PREFIX = "rate:";

    /**
     * Checks if the given IP has exceeded the rate limit.
     * Throws RateLimitExceededException if limit is breached.
     *
     * @param ip the client IP address
     */
    public void checkRateLimit(String ip) {
        String key = RATE_KEY_PREFIX + ip;
        try {
            Long count = redisTemplate.opsForValue().increment(key);

            // First request in window — set expiry
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
            }

            if (count != null && count > maxRequests) {
                log.warn("Rate limit exceeded for IP: {} (count={})", ip, count);
                throw new RateLimitExceededException(ip);
            }

        } catch (RateLimitExceededException e) {
            throw e;   // rethrow — don't swallow this one
        } catch (Exception e) {
            // Redis down → fail open (allow request) to avoid service disruption
            log.warn("Rate limit Redis check failed for IP {}: {} — allowing request", ip, e.getMessage());
        }
    }

    /**
     * Returns remaining requests for the given IP in the current window.
     * Used for X-RateLimit-Remaining response header.
     */
    public int getRemainingRequests(String ip) {
        String key = RATE_KEY_PREFIX + ip;
        try {
            String val = redisTemplate.opsForValue().get(key);
            int used = val != null ? Integer.parseInt(val) : 0;
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            return maxRequests;
        }
    }
}
