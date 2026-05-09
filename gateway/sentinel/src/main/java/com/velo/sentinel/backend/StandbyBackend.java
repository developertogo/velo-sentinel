package com.velo.sentinel.backend;

import com.velo.sentinel.client.StandbyTritonClient;
import com.velo.sentinel.grpc.ModelInferResponse;
import com.velo.sentinel.model.PriorityTier;
import com.velo.sentinel.model.ModelPrecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * StandbyBackend: The Cross-Cloud Failover Implementation.
 * Routes traffic to a secondary cloud region when the primary cluster is down.
 */
@Service
public class StandbyBackend implements InferenceBackend {

    private static final Logger log = LoggerFactory.getLogger(StandbyBackend.class);
    private final StandbyTritonClient standbyClient;

    /**
     * Initializes the standby backend with a dedicated cross-region gRPC client.
     * 
     * @param standbyClient The gRPC client connected to the failover region.
     */
    public StandbyBackend(StandbyTritonClient standbyClient) {
        this.standbyClient = standbyClient;
    }

    @Override
    public float infer(float value) {
        return infer(value, "dr-session", "simple");
    }

    @Override
    public float infer(float value, String sessionId) {
        return infer(value, sessionId, "simple");
    }

    @Override
    public float infer(float value, String sessionId, String modelName) {
        log.warn("FAILOVER-ACTIVE [Session: {}]: Routing to STANDBY cluster in secondary cloud.", sessionId);

        ModelInferResponse response = standbyClient.infer(value, modelName);

        if (response.getRawOutputContentsCount() == 0) {
            throw new RuntimeException("Standby region returned empty response");
        }

        byte[] rawBytes = response.getRawOutputContents(0).toByteArray();
        return ByteBuffer.wrap(rawBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat();
    }

    @Override
    public float sentinelExecute(float value, String sessionId, String modelName, PriorityTier priority, int complexity, ModelPrecision precision, boolean useAgenticOptimization) {
        return infer(value, sessionId, modelName);
    }
}
