package com.urlshortener.service;

import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled cleanup job for expired URLs.
 *
 * Runs every hour and:
 *   1. Finds all URLs past their expiryTime that are still marked active
 *   2. Batch soft-deletes them in the DB
 *   3. Evicts their keys from Redis
 *
 * This keeps the DB and cache clean without manual intervention.
 * In production with AWS: replace with a CloudWatch Events rule + Lambda.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCleanupService {

    private final UrlRepository            urlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String CACHE_PREFIX = "url:";

    /**
     * Runs every hour. Cron: "0 0 * * * *" = top of every hour.
     * Change to "0 * * * * *" during development to run every minute.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Running scheduled URL expiry cleanup at {}", now);

        // 1. Find expired URLs to evict from cache
        List<String> expiredCodes = urlRepository.findAllExpiredUrls(now)
                .stream()
                .map(url -> url.getShortCode())
                .toList();

        if (expiredCodes.isEmpty()) {
            log.info("No expired URLs to clean up.");
            return;
        }

        // 2. Batch soft-delete in DB
        int deactivatedCount = urlRepository.deactivateExpiredUrls(now);
        log.info("Deactivated {} expired URLs in DB", deactivatedCount);

        // 3. Evict each from Redis cache
        int evicted = 0;
        for (String code : expiredCodes) {
            try {
                Boolean deleted = redisTemplate.delete(CACHE_PREFIX + code);
                if (Boolean.TRUE.equals(deleted)) evicted++;
            } catch (Exception e) {
                log.warn("Failed to evict expired key [{}] from Redis: {}", code, e.getMessage());
            }
        }
        log.info("Evicted {} expired keys from Redis cache", evicted);
    }
}
