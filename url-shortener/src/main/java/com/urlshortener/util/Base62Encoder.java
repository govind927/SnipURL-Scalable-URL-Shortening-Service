package com.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 Encoder — converts auto-incremented DB IDs into short, URL-safe codes.
 *
 * Why Base62?
 *  - Characters: a-z (26) + A-Z (26) + 0-9 (10) = 62 total
 *  - URL-safe: no special characters, no encoding needed
 *  - Compact:  6 chars covers 62^6 = 56 billion unique codes
 *  - Deterministic: same ID always produces same code (no collisions)
 *
 * Why NOT random / hash?
 *  - Random: collision probability grows with scale (birthday problem)
 *  - MD5/SHA: longer output, requires truncation, still has collision risk
 *  - Base62 from auto-increment ID: mathematically collision-free
 *
 * Example:
 *   encode(1)       → "b"
 *   encode(62)      → "ba"
 *   encode(125890)  → "aZp3"
 */
@Component
public class Base62Encoder {

    private static final String BASE62_CHARS =
        "abcdefghijklmnopqrstuvwxyz" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "0123456789";

    private static final int BASE      = 62;
    private static final int MIN_LEN   = 6;   // minimum short code length

    /**
     * Encodes a positive long ID into a Base62 string.
     * Output is padded to MIN_LEN characters for uniform appearance.
     */
    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number, got: " + id);
        }

        StringBuilder sb = new StringBuilder();
        long value = id;

        while (value > 0) {
            sb.append(BASE62_CHARS.charAt((int)(value % BASE)));
            value /= BASE;
        }

        // Pad with leading 'a' (index 0) to enforce minimum length
        while (sb.length() < MIN_LEN) {
            sb.append(BASE62_CHARS.charAt(0));
        }

        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to its original long ID.
     * Useful for debugging / reverse lookup.
     */
    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + BASE62_CHARS.indexOf(c);
        }
        return result;
    }
}
