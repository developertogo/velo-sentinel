package com.velo.sentinel.backend;

/**
 * InferenceBackend: Core contract for model execution.
 *
 * DESIGN NOTE: ARCHITECTURAL CHOICE (Java 25 Virtual Threads)
 * We have intentionally chosen synchronous (blocking) method signatures over
 * asynchronous wrappers (like CompletableFuture or Project Reactor).
 *
 * Rationale:
 * 1. SIMPLICITY: Virtual Threads (JEP 444) make blocking code as scalable as
 *    non-blocking code, while maintaining sequential readability and debuggability.
 * 2. STRUCTURED CONCURRENCY: This interface is designed to be called within
 *    StructuredTaskScope (JEP 480), which manages subtask lifecycles through
 *    blocking join() operations.
 * 3. STACK TRACES: Synchronous signatures preserve full stack traces, which are
 *    frequently lost in complex asynchronous chains.
 */
import com.velo.sentinel.model.PriorityTier;

public interface InferenceBackend {
  float infer(float value);
  float infer(float value, String sessionId);
  float infer(float value, String sessionId, String modelName);
  
  default float infer(float value, String sessionId, String modelName, PriorityTier priority) {
      return infer(value, sessionId, modelName);
  }
}
