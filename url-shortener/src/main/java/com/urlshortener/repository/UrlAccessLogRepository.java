package com.urlshortener.repository;

import com.urlshortener.model.UrlAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface UrlAccessLogRepository extends JpaRepository<UrlAccessLog, Long> {

    // Most recent clicks first
    List<UrlAccessLog> findByShortCodeOrderByAccessedAtDesc(String shortCode);

    // Total click count from log (more accurate than counter for analytics)
    Long countByShortCode(String shortCode);

    // Phase 3: clicks grouped by day for trend chart
    @Query(value = """
        SELECT DATE(accessed_at) as date, COUNT(*) as clicks
        FROM url_access_logs
        WHERE short_code = :shortCode
          AND accessed_at >= :since
        GROUP BY DATE(accessed_at)
        ORDER BY date ASC
        """, nativeQuery = true)
    List<Map<String, Object>> getClicksByDay(
        @Param("shortCode") String shortCode,
        @Param("since") LocalDateTime since
    );

    // Phase 3: clicks grouped by country
    @Query(value = """
        SELECT country, COUNT(*) as clicks
        FROM url_access_logs
        WHERE short_code = :shortCode AND country IS NOT NULL
        GROUP BY country
        ORDER BY clicks DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Map<String, Object>> getClicksByCountry(@Param("shortCode") String shortCode);
}
