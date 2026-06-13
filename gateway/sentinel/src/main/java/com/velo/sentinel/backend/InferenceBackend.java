package com.velo.sentinel.backend;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;

/**
 * InferenceBackend: The "Contract" for AI execution.
 *
 * In programming, an "Interface" is like a "Contract" or a "Standard Power Plug."
 * It defines a list of things (methods) that a backend MUST be able to do.
 *
 * Because we have this interface, the rest of the system can use Triton, Dynamo,
 * or Metal interchangeably. As long as they follow this contract, they "fit the plug."
 *
 * For a beginner: This is the "Blueprint" that tells all backends how they should
 * behave when they receive a request.
 */
public interface InferenceBackend {
  /**
   * Executes a simple inference call.
   *
   * @param value The input float value.
   * @return The prediction result.
   */
  float infer(float value);

  /**
   * Executes inference with session tracking.
   *
   * @param value The input value.
   * @param sessionId Unique session ID.
   * @return The prediction result.
   */
  default float infer(float value, String sessionId) {
      return infer(value);
  }

  /**
   * Executes inference with session and model targeting.
   *
   * @param value The input value.
   * @param sessionId Unique session ID.
   * @param modelName Name of the target model.
   * @return The prediction result.
   */
  default float infer(float value, String sessionId, String modelName) {
      return infer(value, sessionId);
  }

  /**
   * Executes inference with SLA and complexity hints.
   *
   * @param value The input value.
   * @param sessionId Unique session ID.
   * @param modelName Target model name.
   * @param priority SLA tier.
   * @param complexity Computational complexity score.
   * @return The prediction result.
   */
  default float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity) {
      return infer(value, sessionId, modelName);
  }

  /**
   * Executes inference with full parameter set including precision.
   *
   * @param value The input value.
   * @param sessionId Unique session ID.
   * @param modelName Target model name.
   * @param priority SLA tier.
   * @param complexity Complexity score.
   * @param precision Desired quantization level.
   * @return The prediction result.
   */
  default float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision) {
      return infer(value, sessionId, modelName, priority, complexity);
  }

  /**
   * High-fidelity execution with agentic optimization support.
   *
   * @param value The input value.
   * @param sessionId Unique session ID.
   * @param modelName Target model name.
   * @param priority SLA tier.
   * @param complexity Complexity score.
   * @param precision Quantization level.
   * @param useAgenticOptimization Enable internal routing optimizations.
   * @return The prediction result.
   */
  float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization);

  /**
   * Executes a text-based inference (Mock).
   *
   * @param prompt The input text prompt.
   * @return The generated text result.
   */
  default String inferText(String prompt) {
      return inferText(prompt, "default-session", "simple");
  }

  /**
   * Executes a text-based inference with session and model context (Mock).
   *
   * @param prompt The input text prompt.
   * @param sessionId Unique session ID.
   * @param modelName Target model name.
   * @return The generated text result.
   */
  default String inferText(String prompt, String sessionId, String modelName) {
      return "Result for: " + prompt;
  }
}
