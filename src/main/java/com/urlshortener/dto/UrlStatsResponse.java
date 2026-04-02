package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response body for GET /api/stats/{shortCode}
 */
@Data
@Builder
public class UrlStatsResponse {

    private String shortCode;
    private String shortUrl;
    private String longUrl;

    // Summary counter (fast)
    private Long clickCount;

    private LocalDateTime createdAt;
    private LocalDateTime expiryTime;
    private boolean isActive;

    // Phase 3: trend data — clicks grouped by day
    private List<Map<String, Object>> clicksByDay;

    // Phase 3: geo data — clicks grouped by country
    private List<Map<String, Object>> clicksByCountry;

    // Last N access events
    private List<RecentClick> recentClicks;

    @Data
    @Builder
    public static class RecentClick {
        private String ipAddress;
        private String country;
        private LocalDateTime accessedAt;
    }
}
