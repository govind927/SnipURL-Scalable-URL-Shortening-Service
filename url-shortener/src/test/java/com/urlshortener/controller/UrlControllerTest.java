package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.UrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.security.JwtAuthFilter;
import com.urlshortener.security.JwtService;
import com.urlshortener.service.RateLimitService;
import com.urlshortener.service.UrlService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice test — only loads the web layer (no DB, no Redis).
 * Uses @WebMvcTest to test HTTP behaviour in isolation.
 */
@WebMvcTest(UrlController.class)
@DisplayName("UrlController MVC Tests")
class UrlControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockBean  private UrlService       urlService;
    @MockBean  private RateLimitService rateLimitService;
    @MockBean  private JwtService       jwtService;
    @MockBean  private JwtAuthFilter    jwtAuthFilter;
    @MockBean  private UserDetailsService userDetailsService;

    // ════════════════════════════════════════════════════════════════
    //  POST /api/shorten
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /api/shorten — 201 with shortUrl in response body")
    void shorten_returns201WithShortUrl() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setLongUrl("https://www.google.com");

        UrlResponse response = UrlResponse.builder()
                .shortCode("aaaaab")
                .shortUrl("http://localhost:8080/aaaaab")
                .longUrl("https://www.google.com")
                .createdAt(LocalDateTime.now())
                .message("Short URL created successfully")
                .build();

        doNothing().when(rateLimitService).checkRateLimit(any());
        when(urlService.shortenUrl(any(UrlRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("aaaaab"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aaaaab"))
                .andExpect(jsonPath("$.longUrl").value("https://www.google.com"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/shorten — 400 when longUrl is blank")
    void shorten_returns400WhenLongUrlBlank() throws Exception {
        String badBody = "{\"longUrl\": \"\"}";

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/shorten — 400 when longUrl is not http/https")
    void shorten_returns400WhenUrlInvalidFormat() throws Exception {
        String badBody = "{\"longUrl\": \"ftp://notaweburl.com\"}";

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /{shortCode} — Redirect
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{shortCode} — 302 redirect to original URL")
    void redirect_returns302WithLocationHeader() throws Exception {
        when(urlService.getOriginalUrl(eq("aaaaab"), any(), any()))
                .thenReturn("https://www.google.com");

        mockMvc.perform(get("/aaaaab"))
                .andExpect(status().isFound())                         // 302
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    @DisplayName("GET /{shortCode} — 404 for unknown code")
    void redirect_returns404ForUnknownCode() throws Exception {
        when(urlService.getOriginalUrl(eq("xxxxxx"), any(), any()))
                .thenThrow(new UrlNotFoundException("xxxxxx"));

        mockMvc.perform(get("/xxxxxx"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /{shortCode} — 410 Gone for expired link")
    void redirect_returns410ForExpiredLink() throws Exception {
        when(urlService.getOriginalUrl(eq("expred"), any(), any()))
                .thenThrow(new UrlExpiredException("expred"));

        mockMvc.perform(get("/expred"))
                .andExpect(status().isGone())                          // 410
                .andExpect(jsonPath("$.status").value(410));
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/stats/{shortCode}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/stats/{shortCode} — 200 with analytics data")
    void getStats_returns200WithClickCount() throws Exception {
        UrlStatsResponse stats = UrlStatsResponse.builder()
                .shortCode("aaaaab")
                .shortUrl("http://localhost:8080/aaaaab")
                .longUrl("https://www.google.com")
                .clickCount(42L)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .recentClicks(Collections.emptyList())
                .build();

        when(urlService.getStats("aaaaab")).thenReturn(stats);

        mockMvc.perform(get("/api/stats/aaaaab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("aaaaab"))
                .andExpect(jsonPath("$.clickCount").value(42))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("GET /api/stats/{shortCode} — 404 for unknown code")
    void getStats_returns404ForUnknown() throws Exception {
        when(urlService.getStats("ghost1"))
                .thenThrow(new UrlNotFoundException("ghost1"));

        mockMvc.perform(get("/api/stats/ghost1"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    //  DELETE /api/{shortCode}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /api/{shortCode} — 204 No Content on success")
    void deleteUrl_returns204() throws Exception {
        doNothing().when(urlService).deleteUrl("aaaaab");

        mockMvc.perform(delete("/api/aaaaab"))
                .andExpect(status().isNoContent());                    // 204

        verify(urlService, times(1)).deleteUrl("aaaaab");
    }

    @Test
    @DisplayName("DELETE /api/{shortCode} — 404 for unknown code")
    void deleteUrl_returns404ForUnknown() throws Exception {
        doThrow(new UrlNotFoundException("ghost1"))
                .when(urlService).deleteUrl("ghost1");

        mockMvc.perform(delete("/api/ghost1"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    //  Health
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/health — 200 OK")
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}