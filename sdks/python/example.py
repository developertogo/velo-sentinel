from sentinel_sdk.client import SentinelClient
def main():
    with SentinelClient(host="localhost", port=8080) as client:
        for val in [42.0, 100.5]:
            response = client.infer(val, model_name="phi-2-metal")
            if response: print(f"SUCCESS: Result: {response['result']:.4f} | Latency: {response['latency_ms']:.2f}ms")
if __name__ == "__main__":
    main()
