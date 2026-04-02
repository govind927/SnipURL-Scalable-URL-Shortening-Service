package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Core entity representing a shortened URL mapping.
 *
 * Design decisions:
 *  - shortCode is indexed for O(1) lookup speed (most frequent query)
 *  - isActive enables soft-delete (preserve analytics data on "delete")
 *  - clickCount is a fast counter; event-level data lives in UrlAccessLog
 *  - creatorIp is stored for rate-limiting and abuse prevention
 */
@Entity
@Table(
    name = "urls",
    indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Original long URL — supports up to 2048 chars (browser max)
    @Column(nullable = false, length = 2048)
    private String longUrl;

    // The short code e.g. "aB3xYz" — unique, indexed
    @Column(unique = true, length = 50)
    private String shortCode;

    // Auto-set on insert via @CreationTimestamp
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Null = never expires; set for temporary links
    @Column
    private LocalDateTime expiryTime;

    // Fast click counter (incremented on each redirect)
    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    // Soft delete — preserves analytics even after user "deletes" link
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Custom alias requested by user (Phase 2)
    @Column(length = 50)
    private String customAlias;

    // IP of the creator — used for rate limiting
    @Column(length = 45)
    private String creatorIp;
}
