package com.urlshortener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Resolves an IP address to a country and city using ip-api.com.
 *
 * Why ip-api.com?
 *  - Completely free for non-commercial use
 *  - No API key required
 *  - 45 requests/minute on free tier — sufficient for a college project
 *  - Returns JSON with country, city, regionName, lat, lon
 *
 * Failure behaviour:
 *  - Any network error / timeout returns GeoResult with nulls
 *  - Geo lookup failure NEVER breaks the redirect — it's best-effort only
 *
 * localhost / private IPs (127.x, 10.x, 192.168.x):
 *  - ip-api returns a specific error for private ranges
 *  - We detect this and return nulls silently (expected during local dev)
 */
@Service
@Slf4j
public class GeoLocationService {

    private static final String GEO_API_URL =
        "http://ip-api.com/json/%s?fields=status,country,countryCode,city,query";

    private final RestTemplate restTemplate = new RestTemplate();

    public record GeoResult(String country, String city) {}

    /**
     * Looks up the country and city for the given IP address.
     * Returns a GeoResult with null fields if lookup fails or IP is private.
     *
     * @param ip IPv4 or IPv6 address string
     * @return GeoResult (never null, but fields inside may be null)
     */
    public GeoResult lookup(String ip) {
        if (ip == null || ip.isBlank() || isPrivateOrLocalIp(ip)) {
            return new GeoResult(null, null);
        }

        try {
            String url = String.format(GEO_API_URL, ip);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                return new GeoResult(null, null);
            }

            String status = (String) response.get("status");
            if (!"success".equals(status)) {
                // ip-api returns "fail" for private ranges and invalid IPs
                log.debug("Geo lookup returned status={} for IP={}", status, ip);
                return new GeoResult(null, null);
            }

            String country = (String) response.get("country");
            String city    = (String) response.get("city");

            log.debug("Geo lookup: IP={} → {}, {}", ip, country, city);
            return new GeoResult(country, city);

        } catch (Exception e) {
            // Network error, timeout, or API down — fail silently
            log.warn("Geo lookup failed for IP {}: {}", ip, e.getMessage());
            return new GeoResult(null, null);
        }
    }

    /**
     * Returns true for loopback, private, and link-local IP ranges.
     * These cannot be resolved to a real location.
     */
    private boolean isPrivateOrLocalIp(String ip) {
        return ip.startsWith("127.")
            || ip.startsWith("10.")
            || ip.startsWith("192.168.")
            || ip.startsWith("172.16.")  || ip.startsWith("172.17.")
            || ip.startsWith("172.18.")  || ip.startsWith("172.19.")
            || ip.startsWith("172.2")    || ip.startsWith("172.30.")
            || ip.startsWith("172.31.")
            || ip.equals("0:0:0:0:0:0:0:1")  // IPv6 loopback
            || ip.equals("::1");
    }
}
