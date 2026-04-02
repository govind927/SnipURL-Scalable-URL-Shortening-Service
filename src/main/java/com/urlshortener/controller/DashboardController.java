package com.urlshortener.controller;

import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.DashboardService;
import com.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard endpoints — all require a valid JWT token.
 *
 * GET    /api/my-urls           → list all URLs created by logged-in user
 * DELETE /api/my-urls/{code}   → delete own URL
 */
@RestController
@RequestMapping("/api/my-urls")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UrlService       urlService;

    // GET /api/my-urls — returns all URLs for the logged-in user
    @GetMapping
    public ResponseEntity<List<UrlStatsResponse>> getMyUrls(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
            dashboardService.getMyUrls(userDetails.getUsername())
        );
    }

    // DELETE /api/my-urls/{shortCode} — soft-delete own URL
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteMyUrl(
            @PathVariable String shortCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
