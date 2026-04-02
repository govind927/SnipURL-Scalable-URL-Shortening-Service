package com.urlshortener.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * Fetches Open Graph metadata from the destination URL.
 *
 * What is Open Graph?
 *  - A protocol (by Facebook) that lets websites define
 *    title, description, image for link previews
 *  - Used by WhatsApp, Twitter, Slack for link unfurling
 *  - Tags look like: <meta property="og:title" content="...">
 *
 * We fetch these tags to build the "You are leaving..." preview page.
 *
 * Falls back to:
 *  - <title> tag if og:title is missing
 *  - <meta name="description"> if og:description is missing
 *  - null if og:image is missing (UI shows placeholder)
 *
 * Uses Jsoup — a Java HTML parser.
 * Timeout: 5 seconds. Failure → returns empty preview (never crashes redirect).
 */
@Service
@Slf4j
public class LinkPreviewService {

    private static final int TIMEOUT_MS = 5000;

    public record LinkPreview(
        String title,
        String description,
        String imageUrl,
        String siteName,
        String destinationUrl
    ) {}

    /**
     * Fetches Open Graph metadata for the given URL.
     * Never throws — returns a partial/empty preview on any failure.
     *
     * @param url the destination URL to preview
     * @return LinkPreview with available metadata
     */
    public LinkPreview fetchPreview(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; SnipURL-Preview/1.0)")
                    .get();

            // og:title → fallback to <title>
            String title = meta(doc, "og:title");
            if (title == null || title.isBlank()) {
                title = doc.title();
            }

            // og:description → fallback to meta description
            String description = meta(doc, "og:description");
            if (description == null || description.isBlank()) {
                description = doc.select("meta[name=description]").attr("content");
            }

            String imageUrl  = meta(doc, "og:image");
            String siteName  = meta(doc, "og:site_name");

            // Truncate long descriptions
            if (description != null && description.length() > 200) {
                description = description.substring(0, 200) + "…";
            }

            log.debug("Preview fetched for {}: title={}", url, title);

            return new LinkPreview(
                title.isBlank()       ? extractDomain(url) : title,
                description,
                imageUrl,
                siteName,
                url
            );

        } catch (Exception e) {
            log.warn("Preview fetch failed for {}: {}", url, e.getMessage());
            // Return minimal preview — never block the redirect experience
            return new LinkPreview(
                extractDomain(url),
                null, null, null, url
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String meta(Document doc, String property) {
        String val = doc.select("meta[property=" + property + "]").attr("content");
        return val.isBlank() ? null : val;
    }

    private String extractDomain(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return url;
        }
    }
}
