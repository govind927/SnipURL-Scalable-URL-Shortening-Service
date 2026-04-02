package com.urlshortener.repository;

import com.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    // Primary lookup — used on every redirect
    Optional<Url> findByShortCode(String shortCode);

    // Check existence without loading the full entity (faster)
    boolean existsByShortCode(String shortCode);

    // Atomic increment — avoids race conditions in high-traffic scenarios
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    // Phase 3: find all expired + still active links (for scheduled cleanup)
    @Query("SELECT u FROM Url u WHERE u.expiryTime IS NOT NULL AND u.expiryTime < :now AND u.isActive = true")
    List<Url> findAllExpiredUrls(@Param("now") LocalDateTime now);

    // Phase 3: batch soft-delete expired URLs
    @Modifying
    @Query("UPDATE Url u SET u.isActive = false WHERE u.expiryTime IS NOT NULL AND u.expiryTime < :now AND u.isActive = true")
    int deactivateExpiredUrls(@Param("now") LocalDateTime now);
}
