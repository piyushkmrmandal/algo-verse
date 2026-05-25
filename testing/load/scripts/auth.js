import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  stages: [
    { duration: '30s', target: 10 },  // ramp up
    { duration: '1m',  target: 10 },  // steady state
    { duration: '30s', target: 50 },  // spike
    { duration: '1m',  target: 50 },  // hold spike
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed:   ['rate<0.01'],  // <1% error rate
    errors:            ['rate<0.05'],
  },
};

export default function () {
  // Registration load test
  const registerPayload = JSON.stringify({
    displayName: `User${Math.random().toString(36).slice(2, 8)}`,
    email: `loadtest+${Math.random().toString(36).slice(2)}@example.com`,
    password: 'LoadTest123!',
  });
  const registerRes = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(registerRes, {
    'register: status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'register: has accessToken': (r) => {
      try {
        return JSON.parse(r.body).accessToken !== undefined;
      } catch {
        return false;
      }
    },
  }) || errorRate.add(1);

  sleep(1);

  // Login load test
  const loginPayload = JSON.stringify({ email: 'loadtest@example.com', password: 'LoadTest123!' });
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(loginRes, {
    'login: status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);
}
