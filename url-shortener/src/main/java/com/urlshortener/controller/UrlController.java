package com.urlshortener.controller;

import com.urlshortener.dto.UrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST Controller — URL Shortener API
 *
 * Endpoints:
 *   POST   /api/shorten          → Create short URL
 *   GET    /{shortCode}          → Redirect to original URL
 *   GET    /api/stats/{shortCode}→ Get click analytics
 *   DELETE /api/{shortCode}      → Soft-delete a short URL
 *
 * Design decisions:
 *   - Redirect uses HTTP 302 (not 301) so every click hits our server
 *     and analytics are accurate. 301 would be cached by browsers.
 *   - Rate limiting is applied only on POST /shorten to prevent spam
 *     URL creation. Redirects are not rate-limited.
 *   - Client IP is extracted via X-Forwarded-For to work behind proxies
 *     like Nginx or AWS ALB.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")   // Allow frontend (React) to call this API
public class UrlController {

    private final UrlService       urlService;
    private final RateLimitService rateLimitService;

    // ── POST /api/shorten ───────────────────────────────────────────
    @PostMapping("/api/shorten")
    public ResponseEntity<UrlResponse> shorten(
            @Valid @RequestBody UrlRequest request,
            HttpServletRequest httpRequest) {

        String ip = extractClientIp(httpRequest);

        // Apply rate limiting on creation endpoint
        rateLimitService.checkRateLimit(ip);

        UrlResponse response = urlService.shortenUrl(request, ip);

        // Add rate limit info to response headers (standard practice)
        int remaining = rateLimitService.getRemainingRequests(ip);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(response);
    }

    // ── GET /{shortCode} ─────────────────────────────────────────────
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        String ip        = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        String longUrl = urlService.getOriginalUrl(shortCode, ip, userAgent);

        // 302 = temporary redirect — browser does NOT cache it
        // This ensures every click is tracked server-side
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    // ── GET /api/stats/{shortCode} ───────────────────────────────────
    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<UrlStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }

    // ── DELETE /api/{shortCode} ──────────────────────────────────────
    @DeleteMapping("/api/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();   // 204 No Content
    }

    // ── Health check ────────────────────────────────────────────────
    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("URL Shortener is running");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Extracts the real client IP behind load balancers and reverse proxies.
     * Nginx / AWS ALB populates X-Forwarded-For with the original client IP.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated chain — first IP is the client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
