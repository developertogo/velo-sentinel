package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrivacyScrubberService: High-performance PII Redaction Interceptor.
 * 
 * Implements Privacy-by-Design by redacting sensitive data (Emails, SSNs, Credit Cards)
 * before it leaves the secure gateway boundary. Uses pre-compiled patterns for
 * sub-millisecond overhead.
 */
@Service
public class PrivacyScrubberService {
    private static final Logger log = LoggerFactory.getLogger(PrivacyScrubberService.class);

    /**
     * Initializes the privacy scrubber with pre-compiled regex patterns.
     */
    public PrivacyScrubberService() {}

    // Pre-compiled patterns for Tier-1 performance
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b");
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d[ -]*?){13,16}\\b");

    /**
     * Scrubs the input text of any PII.
     * 
     * @param text The raw input text.
     * @return The redacted text.
     */
    public String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        long start = System.nanoTime();
        String original = text;

        // Sequence of redaction
        text = redact(text, EMAIL_PATTERN, "[EMAIL_REDACTED]");
        text = redact(text, SSN_PATTERN, "[SSN_REDACTED]");
        text = redact(text, CREDIT_CARD_PATTERN, "[CARD_REDACTED]");

        if (!text.equals(original)) {
            long duration = (System.nanoTime() - start) / 1000;
            log.info("PRIVACY-SCRUBBER: Redacted PII from request in {}μs.", duration);
        }

        return text;
    }

    private String redact(String text, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(replacement);
    }
}
