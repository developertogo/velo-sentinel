import unittest
from unittest.mock import MagicMock, patch
from sentinel_sdk.client import SentinelClient
import struct

class TestSentinelClient(unittest.TestCase):

    @patch('grpc.insecure_channel')
    @patch('sentinel_sdk.grpc_service_pb2_grpc.GRPCInferenceServiceStub')
    def test_client_initialization(self, mock_stub, mock_channel):
        client = SentinelClient(host="localhost", port=9000)
        self.assertEqual(client.target, "localhost:9000")
        mock_channel.assert_called_once_with("localhost:9000")
        self.assertIsNotNone(client.stub)

    @patch('grpc.insecure_channel')
    @patch('sentinel_sdk.grpc_service_pb2_grpc.GRPCInferenceServiceStub')
    def test_infer_request_construction(self, mock_stub, mock_channel):
        client = SentinelClient()
        
        # Mock the gRPC response
        mock_response = MagicMock()
        # Pack a float (42.0) into little-endian binary
        mock_response.raw_output_contents = [struct.pack('<f', 42.0)]
        mock_stub.return_value.ModelInfer.return_value = mock_response
        
        result = client.infer(10.0, model_name="test-model")
        
        # Verify request call
        args, kwargs = mock_stub.return_value.ModelInfer.call_args
        request = args[0]
        
        self.assertEqual(request.model_name, "test-model")
        self.assertEqual(len(request.inputs), 1)
        self.assertEqual(request.inputs[0].contents.fp32_contents[0], 10.0)
        
        # Verify response parsing
        self.assertEqual(result['result'], 42.0)

    @patch('grpc.insecure_channel')
    @patch('sentinel_sdk.grpc_service_pb2_grpc.GRPCInferenceServiceStub')
    def test_infer_empty_response(self, mock_stub, mock_channel):
        client = SentinelClient()
        mock_response = MagicMock()
        mock_response.raw_output_contents = []
        mock_stub.return_value.ModelInfer.return_value = mock_response
        
        result = client.infer(10.0)
        self.assertIsNone(result)

if __name__ == '__main__':
    unittest.main()
