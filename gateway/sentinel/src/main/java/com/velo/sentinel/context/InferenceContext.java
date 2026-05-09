package com.velo.sentinel.context;

import java.lang.ScopedValue;
import org.slf4j.MDC;

/**
 * InferenceContext: Thread-local and Scoped-value management for request state.
 * 
 * Leveraging Java 25 ScopedValues to provide zero-overhead propagation of 
 * session identifiers across virtual thread boundaries.
 */
public final class InferenceContext {
    /** The primary scoped value for the active session ID. */
    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
    
    /** The MDC key used for structured logging. */
    public static final String MDC_SESSION_ID = "sessionId";

    /**
     * Executes a task within a scoped context, ensuring both ScopedValue and MDC are bound.
     * 
     * @param <T> The result type of the task.
     * @param sessionId The session identifier to bind to the context.
     * @param task The task to execute within the session context.
     * @return The result of the task execution.
     * @throws Exception If the task execution fails.
     */
    public static <T> T runInContext(String sessionId, java.util.concurrent.Callable<T> task) throws Exception {
        return ScopedValue.where(SESSION_ID, sessionId)
            .call(() -> {
                MDC.put(MDC_SESSION_ID, sessionId);
                try {
                    return task.call();
                } finally {
                    MDC.remove(MDC_SESSION_ID);
                }
            });
    }
}
