package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response body for POST /api/shorten
 */
@Data
@Builder
public class UrlResponse {

    private String shortCode;

    // Full clickable URL e.g. "https://yourapp.com/aB3xYz"
    private String shortUrl;

    private String longUrl;

    private LocalDateTime createdAt;

    // null if the link never expires
    private LocalDateTime expiryTime;

    private String message;
}
