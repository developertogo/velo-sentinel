package com.velo.sentinel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrivacyScrubberServiceTests {

    private final PrivacyScrubberService scrubber = new PrivacyScrubberService();

    @Test
    void testScrubEmail() {
        String input = "My email is test@example.com";
        String expected = "My email is [EMAIL_REDACTED]";
        assertEquals(expected, scrubber.scrub(input));
    }

    @Test
    void testScrubSSN() {
        String input = "Social Security: 123-45-6789";
        String expected = "Social Security: [SSN_REDACTED]";
        assertEquals(expected, scrubber.scrub(input));
    }

    @Test
    void testScrubCreditCard() {
        String input = "Pay with 1234-5678-9012-3456";
        String expected = "Pay with [CARD_REDACTED]";
        assertEquals(expected, scrubber.scrub(input));
    }

    @Test
    void testNoPII() {
        String input = "Hello, world!";
        assertEquals(input, scrubber.scrub(input));
    }

    @Test
    void testEmptyInput() {
        assertNull(scrubber.scrub(null));
        assertEquals("", scrubber.scrub(""));
    }
}
