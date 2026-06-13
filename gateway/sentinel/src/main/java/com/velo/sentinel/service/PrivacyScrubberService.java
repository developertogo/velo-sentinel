package com.velo.sentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrivacyScrubberService: The "Black Marker" of the System.
 *
 * Imagine you're sending a letter, but it contains secrets like your email address or credit card number.
 * You don't want the AI (which lives in the cloud) to see these secrets.
 *
 * This service acts like a person with a thick black marker. Before the letter (your prompt)
 * leaves our secure building, this service scans it for "PII" (Personally Identifiable Information)
 * and crosses it out with things like "[EMAIL_REDACTED]".
 *
 * It uses "Regex" (Regular Expressions), which are like "blueprints" for finding specific text:
 * - An email blueprint looks for something with an "@" and a ".com".
 * - A credit card blueprint looks for a sequence of 16 numbers.
 */
@Service
public class PrivacyScrubberService {
    private static final Logger log = LoggerFactory.getLogger(PrivacyScrubberService.class);

    /**
     * Initializes the privacy scrubber with pre-compiled regex patterns.
     */
    public PrivacyScrubberService() {}

    // Blueprints (Patterns) for finding secrets.
    // We "pre-compile" them so the scanner is extremely fast (sub-millisecond).

    /** Blueprint for finding email addresses. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}", Pattern.CASE_INSENSITIVE);

    /** Blueprint for finding Social Security Numbers (USA). */
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    /** Blueprint for finding Credit Card numbers. */
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
