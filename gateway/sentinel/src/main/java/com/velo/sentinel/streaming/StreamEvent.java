package com.velo.sentinel.streaming;

/**
 * StreamEvent: A single token or update in an inference stream.
 */
public record StreamEvent(
    String sessionId,
    int tokenIndex,
    String content,
    boolean isLast
) {}
