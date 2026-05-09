package com.velo.sentinel.streaming;

/**
 * StreamEvent: A single token or update in an inference stream.
 * 
 * @param sessionId The active session identifier.
 * @param tokenIndex The sequence position of this token in the stream.
 * @param content The actual text/token payload.
 * @param isLast Whether this is the final event in the stream (EOS).
 */
public record StreamEvent(
    String sessionId,
    int tokenIndex,
    String content,
    boolean isLast
) {}
