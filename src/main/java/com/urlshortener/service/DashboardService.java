package com.urlshortener.service;

import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.model.Url;
import com.urlshortener.model.User;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serves the logged-in user's personal dashboard.
 *
 * Returns all URLs created by the authenticated user,
 * sorted by creation date descending (newest first).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UrlRepository  urlRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Returns all URLs created by the given user email.
     * Each item includes click count, expiry, and active status.
     */
    public List<UrlStatsResponse> getMyUrls(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        List<Url> urls = urlRepository.findByUserOrderByCreatedAtDesc(user);

        return urls.stream()
                .map(url -> UrlStatsResponse.builder()
                        .shortCode(url.getShortCode())
                        .shortUrl(baseUrl + "/" + url.getShortCode())
                        .longUrl(url.getLongUrl())
                        .clickCount(url.getClickCount())
                        .createdAt(url.getCreatedAt())
                        .expiryTime(url.getExpiryTime())
                        .isActive(url.getIsActive())
                        .build())
                .toList();
    }
}
