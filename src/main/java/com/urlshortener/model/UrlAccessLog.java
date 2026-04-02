package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores every individual redirect event for event-level analytics.
 *
 * Why separate from Url.clickCount?
 *  - clickCount = fast integer counter for dashboard summary
 *  - UrlAccessLog = full event data for trends, geo, user-agent analysis
 *
 * This is what separates an average project from a strong one.
 */
@Entity
@Table(
    name = "url_access_logs",
    indexes = {
        @Index(name = "idx_log_short_code", columnList = "shortCode"),
        @Index(name = "idx_log_accessed_at", columnList = "accessedAt")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String shortCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    // IPv4 or IPv6
    @Column(length = 45)
    private String ipAddress;

    // Browser / client info
    @Column(length = 512)
    private String userAgent;

    // Phase 3: populated via IP geolocation lookup
    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;
}
