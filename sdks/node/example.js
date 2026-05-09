const { SentinelClient } = require('./index');
async function run() {
    const client = new SentinelClient('localhost', 8080);
    try {
        const response = await client.infer(42.0, 'phi-2-metal');
        if (response) console.log(`SUCCESS: Result: ${response.result} | Latency: ${response.latency_ms}ms`);
    } finally {
        client.close();
    }
}
run();
