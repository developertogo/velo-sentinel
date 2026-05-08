import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 1000 }, // Ramp up to 1000 users
    { duration: '1m', target: 5000 },  // Stay at 5000 users
    { duration: '30s', target: 10000 }, // Spike to 10000 users
    { duration: '1m', target: 10000 },  // Soak test at 10000 users
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<200'], // 99% of requests must complete below 200ms
  },
};

export default function () {
  const url = 'http://localhost:8080/infer';
  const payload = JSON.stringify({
    value: Math.random() * 100,
    sessionId: `bench-user-${__VU}-${__ITER}`,
    modelName: 'llama-3-8b',
    priority: 'HIGH',
    complexity: 10,
    precision: 'FP16',
    useAgenticOptimization: true
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': 'velo-key-123' // Mocking auth
    },
  };

  const res = http.post(url, payload, params);
  check(res, {
    'is status 200': (r) => r.status === 200,
    'is status 429': (r) => r.status === 429, // Throttling is acceptable
  });

  sleep(0.1); // Small sleep to simulate user think time
}
