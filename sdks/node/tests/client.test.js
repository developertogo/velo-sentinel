const { SentinelClient } = require('../index');
const grpc = require('@grpc/grpc-js');

// Mock grpc-js
jest.mock('@grpc/grpc-js', () => ({
    loadPackageDefinition: jest.fn().mockReturnValue({
        GRPCInferenceService: jest.fn().mockImplementation(() => ({
            ModelInfer: jest.fn(),
            close: jest.fn()
        }))
    }),
    credentials: {
        createInsecure: jest.fn()
    }
}));

// Mock proto-loader
jest.mock('@grpc/proto-loader', () => ({
    loadSync: jest.fn().mockReturnValue({})
}));

describe('SentinelClient', () => {
    let client;

    beforeEach(() => {
        client = new SentinelClient('localhost', 8080);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('should initialize with correct target', () => {
        expect(client.target).toBe('localhost:8080');
    });

    test('should execute inference successfully', async () => {
        const mockResponse = {
            raw_output_contents: [Buffer.alloc(4)]
        };
        mockResponse.raw_output_contents[0].writeFloatLE(42.0, 0);

        // Access the mocked client instance
        client.client.ModelInfer.mockImplementation((request, callback) => {
            callback(null, mockResponse);
        });

        const result = await client.infer(10.0, 'test-model');
        
        expect(result.result).toBeCloseTo(42.0);
        expect(result.model).toBe('test-model');
        expect(client.client.ModelInfer).toHaveBeenCalled();
    });

    test('should handle gRPC errors', async () => {
        const mockError = new Error('Connection failed');
        client.client.ModelInfer.mockImplementation((request, callback) => {
            callback(mockError, null);
        });

        await expect(client.infer(10.0)).rejects.toThrow('Connection failed');
    });
});
