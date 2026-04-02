package com.urlshortener.service;

import com.urlshortener.dto.UrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.model.UrlAccessLog;
import com.urlshortener.service.GeoLocationService;
import com.urlshortener.repository.UrlAccessLogRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for the URL shortening service.
 *
 * Caching strategy — Cache-Aside Pattern:
 *   1. On redirect: check Redis first (cache hit → return immediately)
 *   2. On cache miss: query MySQL, then populate Redis cache
 *   3. On Redis failure: silently fall through to DB (fault tolerance)
 *   4. On delete: evict from cache immediately
 *
 * Redis key format:  "url:{shortCode}"
 * Redis value:       the original long URL (plain string)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository            urlRepository;
    private final UrlAccessLogRepository   accessLogRepository;
    private final Base62Encoder            base62Encoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final GeoLocationService       geoLocationService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.cache.ttl-hours:24}")
    private long cacheTtlHours;

    // ── Redis key prefix ────────────────────────────────────────────
    private static final String CACHE_PREFIX = "url:";

    // ════════════════════════════════════════════════════════════════
    //  CREATE — POST /api/shorten
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public UrlResponse shortenUrl(UrlRequest request, String creatorIp) {

        // ── Custom alias path ─────────────────────────────────────
        if (hasCustomAlias(request)) {
            return createWithCustomAlias(request, creatorIp);
        }

        // ── Auto-generated Base62 path ────────────────────────────
        return createWithGeneratedCode(request, creatorIp);
    }

    private UrlResponse createWithCustomAlias(UrlRequest request, String creatorIp) {
        String alias = request.getCustomAlias().trim();

        if (urlRepository.existsByShortCode(alias)) {
            throw new CustomAliasAlreadyExistsException(alias);
        }

        Url url = Url.builder()
                .longUrl(request.getLongUrl())
                .shortCode(alias)
                .customAlias(alias)
                .expiryTime(request.getExpiryTime())
                .creatorIp(creatorIp)
                .clickCount(0L)
                .isActive(true)
                .build();

        Url saved = urlRepository.save(url);
        cacheUrl(alias, saved.getLongUrl());

        log.info("Created custom alias [{}] → {}", alias, saved.getLongUrl());
        return toResponse(saved);
    }

    private UrlResponse createWithGeneratedCode(UrlRequest request, String creatorIp) {
        // Step 1: save without shortCode to get the auto-generated DB id
        Url url = Url.builder()
                .longUrl(request.getLongUrl())
                .expiryTime(request.getExpiryTime())
                .creatorIp(creatorIp)
                .clickCount(0L)
                .isActive(true)
                .build();

        Url saved = urlRepository.save(url);

        // Step 2: encode the DB id → Base62 short code
        String shortCode = base62Encoder.encode(saved.getId());
        saved.setShortCode(shortCode);
        urlRepository.save(saved);

        // Step 3: prime the cache immediately (avoids first-hit DB call)
        cacheUrl(shortCode, saved.getLongUrl());

        log.info("Created short code [{}] → {}", shortCode, saved.getLongUrl());
        return toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════════
    //  REDIRECT — GET /{shortCode}
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public String getOriginalUrl(String shortCode, String ipAddress, String userAgent) {

        // ── Step 1: Redis cache check ─────────────────────────────
        String cachedUrl = getFromCache(shortCode);

        if (cachedUrl != null) {
            log.debug("Cache HIT  → [{}]", shortCode);
            recordAccess(shortCode, ipAddress, userAgent);  // still track clicks
            return cachedUrl;
        }

        // ── Step 2: Cache miss — query DB ─────────────────────────
        log.debug("Cache MISS → [{}] — querying DB", shortCode);

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // ── Step 3: Validate ──────────────────────────────────────
        if (!url.getIsActive()) {
            throw new UrlNotFoundException(shortCode);
        }

        if (isExpired(url)) {
            throw new UrlExpiredException(shortCode);
        }

        // ── Step 4: Populate cache ────────────────────────────────
        cacheUrl(shortCode, url.getLongUrl());

        // ── Step 5: Track the access ──────────────────────────────
        recordAccess(shortCode, ipAddress, userAgent);

        return url.getLongUrl();
    }

    // ════════════════════════════════════════════════════════════════
    //  ANALYTICS — GET /api/stats/{shortCode}
    // ════════════════════════════════════════════════════════════════

    public UrlStatsResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Last 10 access events
        List<UrlAccessLog> recentLogs = accessLogRepository
                .findByShortCodeOrderByAccessedAtDesc(shortCode)
                .stream()
                .limit(10)
                .collect(Collectors.toList());

        List<UrlStatsResponse.RecentClick> recentClicks = recentLogs.stream()
                .map(log -> UrlStatsResponse.RecentClick.builder()
                        .ipAddress(maskIp(log.getIpAddress()))   // privacy: mask last octet
                        .country(log.getCountry())
                        .accessedAt(log.getAccessedAt())
                        .build())
                .collect(Collectors.toList());

        // Phase 3: click trend for last 30 days
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<java.util.Map<String, Object>> clicksByDay =
                accessLogRepository.getClicksByDay(shortCode, since);
        List<java.util.Map<String, Object>> clicksByCountry =
                accessLogRepository.getClicksByCountry(shortCode);

        return UrlStatsResponse.builder()
                .shortCode(shortCode)
                .shortUrl(baseUrl + "/" + shortCode)
                .longUrl(url.getLongUrl())
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiryTime(url.getExpiryTime())
                .isActive(url.getIsActive())
                .recentClicks(recentClicks)
                .clicksByDay(clicksByDay)
                .clicksByCountry(clicksByCountry)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  DELETE — DELETE /api/{shortCode}
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Soft delete — keep the record for analytics history
        url.setIsActive(false);
        urlRepository.save(url);

        // Evict from cache immediately
        evictFromCache(shortCode);

        log.info("Soft-deleted short code [{}]", shortCode);
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Records click count increment + access log entry.
     * Called on every successful redirect (cache hit or miss).
     */
    private void recordAccess(String shortCode, String ipAddress, String userAgent) {
        // Atomic SQL increment — no race conditions
        urlRepository.incrementClickCount(shortCode);

        // Geo lookup — best-effort, never blocks the redirect
        GeoLocationService.GeoResult geo = geoLocationService.lookup(ipAddress);

        // Store full event for analytics
        UrlAccessLog accessLog = UrlAccessLog.builder()
                .shortCode(shortCode)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .country(geo.country())
                .city(geo.city())
                .build();
        accessLogRepository.save(accessLog);
    }

    /** Write to Redis with TTL. Silently ignores Redis failures. */
    private void cacheUrl(String shortCode, String longUrl) {
        try {
            redisTemplate.opsForValue().set(
                CACHE_PREFIX + shortCode,
                longUrl,
                Duration.ofHours(cacheTtlHours)
            );
        } catch (Exception e) {
            // Redis failure must NEVER crash the main flow
            log.warn("Redis write failed for [{}]: {}", shortCode, e.getMessage());
        }
    }

    /** Read from Redis. Returns null on miss or failure. */
    private String getFromCache(String shortCode) {
        try {
            return redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis read failed for [{}] — falling back to DB: {}", shortCode, e.getMessage());
            return null;   // graceful degradation — fall through to DB
        }
    }

    /** Evict a key from Redis on delete/expire. */
    private void evictFromCache(String shortCode) {
        try {
            redisTemplate.delete(CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis eviction failed for [{}]: {}", shortCode, e.getMessage());
        }
    }

    private boolean isExpired(Url url) {
        return url.getExpiryTime() != null
                && url.getExpiryTime().isBefore(LocalDateTime.now());
    }

    private boolean hasCustomAlias(UrlRequest request) {
        return request.getCustomAlias() != null
                && !request.getCustomAlias().isBlank();
    }

    /** Mask last IPv4 octet for privacy: 192.168.1.42 → 192.168.1.*** */
    private String maskIp(String ip) {
        if (ip == null) return null;
        int lastDot = ip.lastIndexOf('.');
        return lastDot >= 0 ? ip.substring(0, lastDot) + ".***" : "***";
    }

    private UrlResponse toResponse(Url url) {
        return UrlResponse.builder()
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .longUrl(url.getLongUrl())
                .createdAt(url.getCreatedAt())
                .expiryTime(url.getExpiryTime())
                .message("Short URL created successfully")
                .build();
    }
}
