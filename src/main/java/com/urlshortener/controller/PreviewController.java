package com.urlshortener.controller;

import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.LinkPreviewService;
import com.urlshortener.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles link preview and QR code endpoints.
 *
 * GET /api/preview/{shortCode}  → returns JSON with og metadata
 * GET /api/qr/{shortCode}       → returns QR code PNG image
 *
 * Both are public — no auth required.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PreviewController {

    private final UrlRepository      urlRepository;
    private final LinkPreviewService linkPreviewService;
    private final QrCodeService      qrCodeService;

    @Value("${app.base-url}")
    private String baseUrl;

    // ── GET /api/preview/{shortCode} ─────────────────────────────
    @GetMapping("/api/preview/{shortCode}")
    public ResponseEntity<Map<String, Object>> getPreview(
            @PathVariable String shortCode) {

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        LinkPreviewService.LinkPreview preview =
                linkPreviewService.fetchPreview(url.getLongUrl());

        return ResponseEntity.ok(Map.of(
            "shortCode",      shortCode,
            "shortUrl",       baseUrl + "/" + shortCode,
            "longUrl",        url.getLongUrl(),
            "title",          preview.title()       != null ? preview.title()       : "",
            "description",    preview.description() != null ? preview.description() : "",
            "imageUrl",       preview.imageUrl()    != null ? preview.imageUrl()    : "",
            "siteName",       preview.siteName()    != null ? preview.siteName()    : "",
            "clickCount",     url.getClickCount()
        ));
    }

    // ── GET /api/qr/{shortCode} ──────────────────────────────────
    @GetMapping(value = "/api/qr/{shortCode}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "300") int size) {

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // QR encodes the full short URL (what users scan)
        String shortUrl = baseUrl + "/" + shortCode;
        byte[] qrImage  = qrCodeService.generateQrCode(shortUrl, size);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }
}
