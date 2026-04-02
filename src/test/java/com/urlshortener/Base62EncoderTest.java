package com.urlshortener;

import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    @DisplayName("Encoded output should be at least 6 characters")
    void encodedLengthShouldBeAtLeastSix() {
        String code = encoder.encode(1L);
        assertTrue(code.length() >= 6, "Short code must be at least 6 chars");
    }

    @Test
    @DisplayName("Different IDs must produce different codes — no collisions")
    void shouldProduceUniqueCodesForDifferentIds() {
        Set<String> codes = new HashSet<>();
        for (long i = 1; i <= 10_000; i++) {
            codes.add(encoder.encode(i));
        }
        assertEquals(10_000, codes.size(), "All 10,000 codes must be unique");
    }

    @Test
    @DisplayName("Encoding is deterministic — same ID always returns same code")
    void encodingShouldBeDeterministic() {
        assertEquals(encoder.encode(12345L), encoder.encode(12345L));
    }

    @Test
    @DisplayName("Encoded output should only contain Base62 characters")
    void outputShouldOnlyContainBase62Chars() {
        String validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (long i = 1; i <= 100; i++) {
            String code = encoder.encode(i);
            for (char c : code.toCharArray()) {
                assertTrue(validChars.indexOf(c) >= 0,
                        "Invalid character '" + c + "' in code: " + code);
            }
        }
    }

    @Test
    @DisplayName("Decode reverses encode correctly")
    void decodeShouldReverseEncode() {
        long original = 125890L;
        String encoded = encoder.encode(original);
        long decoded   = encoder.decode(encoded);
        assertEquals(original, decoded, "Decoded value should match original ID");
    }

    @Test
    @DisplayName("Should throw for zero or negative ID")
    void shouldThrowForInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(0L));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(-1L));
    }
}
