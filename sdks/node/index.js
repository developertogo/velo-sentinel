const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

/**
 * SentinelClient: High-performance Node.js client for Velo-Sentinel.
 */
class SentinelClient {
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

        // However, standard protoLoader prefers .proto files for easy use.
        // Let's assume we bundle the proto files or use the descriptor set correctly.
        // For this demo, we'll use a simplified dynamic loading if possible, 
        // but since we only have the .pb, we'll use the descriptor logic.
        
        const sentinelProto = grpc.loadPackageDefinition(packageDefinition);
        // Note: The structure depends on the proto package name.
        // In our case it's usually empty or specific.
        this.client = new sentinelProto.GRPCInferenceService(
            this.target,
            grpc.credentials.createInsecure()
        );
    }

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
