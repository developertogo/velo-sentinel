const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

/**
 * SentinelClient: High-performance Node.js client for Velo-Sentinel.
 */
class SentinelClient {
    /**
     * Creates a new SentinelClient instance.
     * 
     * @param {string} host - The gateway hostname. Defaults to 'localhost'.
     * @param {number} port - The gRPC port. Defaults to 8080.
     */
    constructor(host = 'localhost', port = 8080) {
        this.target = `${host}:${port}`;
        
        // Load the descriptor set we generated
        const descriptorPath = path.join(__dirname, 'sentinel_descriptor.pb');
        
        // In Node.js gRPC, we can load either .proto or .pb
        // For production efficiency, loading the pre-compiled .pb is faster
        const packageDefinition = protoLoader.loadSync('', {
            keepCase: true,
            longs: String,
            enums: String,
            defaults: true,
            oneofs: true,
            includeDirs: [__dirname] // This is a hack for the descriptor set
        });

        const sentinelProto = grpc.loadPackageDefinition(packageDefinition);
        this.client = new sentinelProto.GRPCInferenceService(
            this.target,
            grpc.credentials.createInsecure()
        );
    }

    /**
     * Executes a synchronous inference call.
     * 
     * @param {number} value - The input value.
     * @param {string} modelName - The target model name. Defaults to 'simple'.
     * @returns {Promise<{result: number, latency_ms: number, model: string}|null>}
     */
    async infer(value, modelName = 'simple') {
        const request = {
            model_name: modelName,
            inputs: [{
                name: 'INPUT',
                datatype: 'FP32',
                shape: [1],
                contents: {
                    fp32_contents: [value]
                }
            }]
        };

        return new Promise((resolve, reject) => {
            const startTime = Date.now();
            this.client.ModelInfer(request, (err, response) => {
                if (err) {
                    return reject(err);
                }
                
                const duration = Date.now() - startTime;
                
                // Extract float result from buffer
                if (response.raw_output_contents && response.raw_output_contents.length > 0) {
                    const buffer = response.raw_output_contents[0];
                    const result = buffer.readFloatLE(0);
                    resolve({
                        result,
                        latency_ms: duration,
                        model: modelName
                    });
                } else {
                    resolve(null);
                }
            });
        });
    }

    close() {
        this.client.close();
    }
}

module.exports = { SentinelClient };
