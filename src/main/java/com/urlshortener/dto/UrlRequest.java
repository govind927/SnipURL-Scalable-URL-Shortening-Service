package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Incoming request body for POST /api/shorten
 */
@Data
public class UrlRequest {

    @NotBlank(message = "longUrl must not be blank")
    @Pattern(
        regexp = "^(https?://).+",
        message = "longUrl must start with http:// or https://"
    )
    @Size(max = 2048, message = "longUrl must not exceed 2048 characters")
    private String longUrl;

    // Optional — user can pick their own short code
    @Size(min = 3, max = 30, message = "customAlias must be between 3 and 30 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]*$",
        message = "customAlias can only contain letters, numbers, hyphens, and underscores"
    )
    private String customAlias;

    // Optional — null means the link never expires
    private LocalDateTime expiryTime;
}
