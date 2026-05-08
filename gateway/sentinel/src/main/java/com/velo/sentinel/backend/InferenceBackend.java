package com.velo.sentinel.backend;

import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;

public interface InferenceBackend {
  float infer(float value);

  default float infer(float value, String sessionId) {
      return infer(value);
  }

  default float infer(float value, String sessionId, String modelName) {
      return infer(value, sessionId);
  }

  default float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity) {
      return infer(value, sessionId, modelName);
  }

  default float infer(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision) {
      return infer(value, sessionId, modelName, priority, complexity);
  }

  float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization);
}
