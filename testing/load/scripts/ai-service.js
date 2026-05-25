import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const recommendationsLatency = new Trend('recommendations_latency');
const skillsLatency = new Trend('skills_latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : {}),
};

// NOTE: The AI tutor SSE/streaming endpoint (/api/v1/tutor/stream or similar) is intentionally
// excluded from these load tests. k6 has limited support for Server-Sent Events (SSE) and
// long-lived streaming connections — it does not natively parse SSE frames, and keeping
// many persistent connections open skews resource metrics. To load-test the streaming endpoint,
// use a dedicated SSE-capable tool (e.g. artillery with sse plugin, or a custom k6 extension).

export const options = {
  stages: [
    { duration: '30s', target: 10 }, // gentle ramp — AI calls are expensive
    { duration: '2m',  target: 20 }, // hold at 20 VUs
    { duration: '30s', target: 0 },  // ramp down
  ],
  thresholds: {
    // AI inference can be slow — allow up to 5 seconds at p(95)
    http_req_duration:        ['p(95)<5000'],
    http_req_failed:          ['rate<0.02'], // allow slightly higher error rate for AI
    errors:                   ['rate<0.02'],
    recommendations_latency:  ['p(95)<5000'],
    skills_latency:           ['p(95)<2000'],
  },
};

const RECOMMENDATION_TYPES = ['PROBLEM', 'TOPIC', 'LEARNING_PATH'];

export default function () {
  // Scenario 1: Get personalized problem/topic recommendations
  group('get_recommendations', () => {
    const type = RECOMMENDATION_TYPES[Math.floor(Math.random() * RECOMMENDATION_TYPES.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/recommendations?count=5&type=${type}`,
      { headers: HEADERS }
    );
    recommendationsLatency.add(res.timings.duration);
    check(res, {
      'recommendations: status 200': (r) => r.status === 200,
      'recommendations: returns array': (r) => {
        try {
          const body = JSON.parse(r.body);
          return Array.isArray(body) || Array.isArray(body.recommendations);
        } catch {
          return false;
        }
      },
    }) || errorRate.add(1);
  });

  sleep(1);

  // Scenario 2: Get available skills / skill graph
  group('get_skills', () => {
    const res = http.get(`${BASE_URL}/api/v1/skills`, { headers: HEADERS });
    skillsLatency.add(res.timings.duration);
    check(res, {
      'skills: status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
  });

  // AI calls are resource-intensive — sleep longer between iterations
  sleep(2);
}
