package com.velo.sentinel.context;

import java.lang.ScopedValue;
import org.slf4j.MDC;

public final class InferenceContext {
    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
    public static final String MDC_SESSION_ID = "sessionId";

    /**
     * Executes a task within a scoped context, ensuring both ScopedValue and MDC are bound.
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
