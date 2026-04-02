package com.urlshortener.service;

import com.urlshortener.dto.UrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.model.UrlAccessLog;
import com.urlshortener.repository.UrlAccessLogRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService Unit Tests")
class UrlServiceTest {

    @Mock private UrlRepository            urlRepository;
    @Mock private UrlAccessLogRepository   accessLogRepository;
    @Mock private Base62Encoder            base62Encoder;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private GeoLocationService       geoLocationService;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually (not loaded in unit tests)
        ReflectionTestUtils.setField(urlService, "baseUrl",       "http://localhost:8080");
        ReflectionTestUtils.setField(urlService, "cacheTtlHours", 24L);

        // Default Redis mock — opsForValue() returns our mock ValueOperations
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Default geo mock — return nulls (avoids real HTTP calls)
        lenient().when(geoLocationService.lookup(any()))
                 .thenReturn(new GeoLocationService.GeoResult(null, null));
    }

    // ════════════════════════════════════════════════════════════════
    //  SHORTEN URL
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("shortenUrl — generates Base62 short code from saved ID")
    void shortenUrl_generatesBase62Code() {
        UrlRequest request = new UrlRequest();
        request.setLongUrl("https://www.google.com");

        Url savedUrl = Url.builder()
                .id(1L)
                .longUrl("https://www.google.com")
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .isActive(true)
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);
        when(base62Encoder.encode(1L)).thenReturn("aaaaab");
        lenient().when(valueOps.get(any())).thenReturn(null);

        UrlResponse response = urlService.shortenUrl(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("aaaaab", response.getShortCode());
        assertTrue(response.getShortUrl().contains("aaaaab"));
        assertEquals("https://www.google.com", response.getLongUrl());

        // Verify DB was saved twice — once to get ID, once to set shortCode
        verify(urlRepository, times(2)).save(any(Url.class));
        verify(base62Encoder, times(1)).encode(1L);
    }

    @Test
    @DisplayName("shortenUrl — uses custom alias when provided")
    void shortenUrl_usesCustomAlias() {
        UrlRequest request = new UrlRequest();
        request.setLongUrl("https://github.com");
        request.setCustomAlias("my-repo");

        Url savedUrl = Url.builder()
                .id(99L)
                .longUrl("https://github.com")
                .shortCode("my-repo")
                .customAlias("my-repo")
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .isActive(true)
                .build();

        when(urlRepository.existsByShortCode("my-repo")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);

        UrlResponse response = urlService.shortenUrl(request, "127.0.0.1");

        assertEquals("my-repo", response.getShortCode());
        // Base62 encoder should NOT be called for custom aliases
        verify(base62Encoder, never()).encode(anyLong());
    }

    @Test
    @DisplayName("shortenUrl — throws ConflictException when custom alias is taken")
    void shortenUrl_throwsWhenAliasAlreadyExists() {
        UrlRequest request = new UrlRequest();
        request.setLongUrl("https://example.com");
        request.setCustomAlias("taken-alias");

        when(urlRepository.existsByShortCode("taken-alias")).thenReturn(true);

        assertThrows(CustomAliasAlreadyExistsException.class,
                () -> urlService.shortenUrl(request, "127.0.0.1"));

        verify(urlRepository, never()).save(any());
    }

    // ════════════════════════════════════════════════════════════════
    //  REDIRECT
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getOriginalUrl — returns URL from Redis cache on HIT")
    void getOriginalUrl_cacheHit_returnsWithoutDbQuery() {
        when(valueOps.get("url:aaaaab")).thenReturn("https://www.google.com");

        String result = urlService.getOriginalUrl("aaaaab", "1.2.3.4", "TestAgent");

        assertEquals("https://www.google.com", result);

        // DB should NOT be queried on cache hit
        verify(urlRepository, never()).findByShortCode(any());

        // But click tracking should still happen
        verify(urlRepository, times(1)).incrementClickCount("aaaaab");
        verify(accessLogRepository, times(1)).save(any(UrlAccessLog.class));
    }

    @Test
    @DisplayName("getOriginalUrl — queries DB on cache MISS and populates cache")
    void getOriginalUrl_cacheMiss_queriesDbAndPopulatesCache() {
        Url url = Url.builder()
                .id(1L)
                .longUrl("https://www.example.com")
                .shortCode("aaaaab")
                .isActive(true)
                .clickCount(0L)
                .build();

        when(valueOps.get("url:aaaaab")).thenReturn(null);       // cache miss
        when(urlRepository.findByShortCode("aaaaab")).thenReturn(Optional.of(url));

        String result = urlService.getOriginalUrl("aaaaab", "1.2.3.4", "TestAgent");

        assertEquals("https://www.example.com", result);

        // DB queried once
        verify(urlRepository, times(1)).findByShortCode("aaaaab");
        // Cache populated
        verify(valueOps, times(1)).set(eq("url:aaaaab"), eq("https://www.example.com"), any());
    }

    @Test
    @DisplayName("getOriginalUrl — throws UrlNotFoundException for unknown short code")
    void getOriginalUrl_throwsNotFound() {
        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode("xxxxxx")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class,
                () -> urlService.getOriginalUrl("xxxxxx", "1.2.3.4", "TestAgent"));
    }

    @Test
    @DisplayName("getOriginalUrl — throws UrlExpiredException for expired link")
    void getOriginalUrl_throwsExpired() {
        Url expiredUrl = Url.builder()
                .id(2L)
                .longUrl("https://expired.com")
                .shortCode("expred")
                .isActive(true)
                .expiryTime(LocalDateTime.now().minusHours(1))  // expired 1 hour ago
                .clickCount(5L)
                .build();

        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode("expred")).thenReturn(Optional.of(expiredUrl));

        assertThrows(UrlExpiredException.class,
                () -> urlService.getOriginalUrl("expred", "1.2.3.4", "TestAgent"));
    }

    @Test
    @DisplayName("getOriginalUrl — throws UrlNotFoundException for inactive (soft-deleted) link")
    void getOriginalUrl_throwsForInactiveUrl() {
        Url inactiveUrl = Url.builder()
                .id(3L)
                .longUrl("https://deleted.com")
                .shortCode("deldel")
                .isActive(false)                                  // soft-deleted
                .clickCount(0L)
                .build();

        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode("deldel")).thenReturn(Optional.of(inactiveUrl));

        assertThrows(UrlNotFoundException.class,
                () -> urlService.getOriginalUrl("deldel", "1.2.3.4", "TestAgent"));
    }

    @Test
    @DisplayName("getOriginalUrl — Redis failure falls through to DB gracefully")
    void getOriginalUrl_redisFails_fallsBackToDb() {
        Url url = Url.builder()
                .id(1L)
                .longUrl("https://resilient.com")
                .shortCode("rslnt1")
                .isActive(true)
                .clickCount(0L)
                .build();

        // Redis throws — simulates connection failure
        when(valueOps.get(any())).thenThrow(new RuntimeException("Redis connection refused"));
        when(urlRepository.findByShortCode("rslnt1")).thenReturn(Optional.of(url));

        // Should NOT throw — should degrade to DB
        String result = urlService.getOriginalUrl("rslnt1", "1.2.3.4", "TestAgent");

        assertEquals("https://resilient.com", result);
        verify(urlRepository, times(1)).findByShortCode("rslnt1");
    }

    // ════════════════════════════════════════════════════════════════
    //  DELETE
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteUrl — soft-deletes and evicts from cache")
    void deleteUrl_softDeletesAndEvictsCache() {
        Url url = Url.builder()
                .id(1L)
                .longUrl("https://tobedeleted.com")
                .shortCode("deltme")
                .isActive(true)
                .clickCount(3L)
                .build();

        when(urlRepository.findByShortCode("deltme")).thenReturn(Optional.of(url));
        when(urlRepository.save(any(Url.class))).thenReturn(url);

        urlService.deleteUrl("deltme");

        // isActive should be flipped to false
        assertFalse(url.getIsActive());
        verify(urlRepository, times(1)).save(url);

        // Redis key evicted
        verify(redisTemplate, times(1)).delete("url:deltme");
    }

    @Test
    @DisplayName("deleteUrl — throws UrlNotFoundException for unknown code")
    void deleteUrl_throwsNotFound() {
        when(urlRepository.findByShortCode("ghost1")).thenReturn(Optional.empty());
        assertThrows(UrlNotFoundException.class, () -> urlService.deleteUrl("ghost1"));
    }
}
