# Velo-Sentinel Multi-Language SDKs

This directory contains the high-performance clients for the Velo-Sentinel Inference Gateway. These SDKs are automatically generated from the core gRPC schema (for Node/Python) or provide idiomatic resilience wrappers (for Java).

---

## ☕ Java SDK

### Prerequisites
- JDK 25
- Gradle

### 1. Installation
The Java SDK is a Gradle-based library. You can build the JAR and install it to your local Maven repository:
```bash
cd sdks/java
./gradlew jar 
```

### 2. Running the Example
Ensure the Sentinel Gateway is running, then execute the sample class:
```bash
./gradlew run
```

### 3. Running Tests
Verify the resilience logic (hedging, retries):
```bash
./gradlew test
```

### 4. Generating Documentation
Generate the full Javadoc API reference:
```bash
./gradlew javadoc
```
*Output: `build/docs/javadoc/index.html`*

---

## 🐍 Python SDK

### Prerequisites
- Python 3.9+
- `pip`

### 1. Installation
Navigate to the python directory and install in editable mode:
```bash
cd sdks/python
pip install -e .
```

### 2. Running the Example
Ensure the Sentinel Gateway is running (`./gradlew bootRun`), then execute the sample script:
```bash
python example.py
```

### 3. Running Tests
Verify the client logic without a live gateway using `pytest`:
```bash
pip install pytest
pytest tests/
```

---

## 🟢 Node.js SDK

### Prerequisites
- Node.js 18+
- `npm`

### 1. Installation
Navigate to the node directory and install dependencies:
```bash
cd sdks/node
npm install
```

### 2. Running the Example
Ensure the Sentinel Gateway is running, then execute the sample script:
```bash
node example.js
```

### 3. Running Tests
Verify the SDK logic:
```bash
npm test
```

---

## 🚀 Common Integration Guide

### Connectivity
By default, all SDKs attempt to connect to `localhost:8080`. You can override this in the constructor:

**Java**:
```java
ResilientSentinelClient client = new ResilientSentinelClient("http://10.0.0.5:8080", 50, 3);
```

**Python**:
```python
client = SentinelClient(host="10.0.0.5", port=8080)
```

**Node.js**:
```javascript
const client = new SentinelClient('10.0.0.5', 8080);
```

### Error Handling
The SDKs raise/reject on gRPC errors (e.g., `UNAVAILABLE`, `DEADLINE_EXCEEDED`). Ensure you wrap calls in `try/except` (Python) or `try/catch` (Node.js) blocks for production resilience.

### Performance
For maximum throughput, reuse the `SentinelClient` instance across multiple requests to benefit from gRPC connection pooling.
