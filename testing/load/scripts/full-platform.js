/**
 * AlgoVerse — Full Platform Load Test
 *
 * Simulates realistic concurrent usage across all major platform surfaces:
 *   - Auth flows (5 VUs)
 *   - Browsing problems (20 VUs)
 *   - Leaderboard checks (30 VUs — Redis-backed, high concurrency)
 *   - AI recommendations (5 VUs — expensive, kept low)
 *
 * Run:
 *   k6 run --env BASE_URL_AUTH=http://localhost:8081 \
 *           --env BASE_URL_PROBLEMS=http://localhost:8082 \
 *           --env BASE_URL_GAMIFICATION=http://localhost:8084 \
 *           --env BASE_URL_AI=http://localhost:8085 \
 *           --env AUTH_TOKEN=<jwt> \
 *           testing/load/scripts/full-platform.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

// Service base URLs — each microservice can have its own port / hostname
const BASE_AUTH         = __ENV.BASE_URL_AUTH        || 'http://localhost:8081';
const BASE_PROBLEMS     = __ENV.BASE_URL_PROBLEMS    || 'http://localhost:8082';
const BASE_GAMIFICATION = __ENV.BASE_URL_GAMIFICATION|| 'http://localhost:8084';
const BASE_AI           = __ENV.BASE_URL_AI          || 'http://localhost:8085';
const AUTH_TOKEN        = __ENV.AUTH_TOKEN           || '';

const AUTH_HEADER = AUTH_TOKEN
  ? { Authorization: `Bearer ${AUTH_TOKEN}`, 'Content-Type': 'application/json' }
  : { 'Content-Type': 'application/json' };

// ---------------------------------------------------------------------------
// Scenario executor functions
// ---------------------------------------------------------------------------

export function authFlow() {
  const loginPayload = JSON.stringify({
    email: `loadtest+${Math.random().toString(36).slice(2)}@example.com`,
    password: 'LoadTest123!',
  });
  const res = http.post(`${BASE_AUTH}/api/v1/auth/login`, loginPayload, {
    headers: AUTH_HEADER,
  });
  check(res, {
    'auth flow: login 200 or 401': (r) => r.status === 200 || r.status === 401,
  }) || errorRate.add(1);
  sleep(2);
}

export function browseProblems() {
  const pages = [0, 1, 2];
  const page = pages[Math.floor(Math.random() * pages.length)];
  const difficulties = ['', 'EASY', 'MEDIUM', 'HARD'];
  const difficulty = difficulties[Math.floor(Math.random() * difficulties.length)];
  const url = difficulty
    ? `${BASE_PROBLEMS}/api/v1/problems?page=${page}&size=20&difficulty=${difficulty}`
    : `${BASE_PROBLEMS}/api/v1/problems?page=${page}&size=20`;
  const res = http.get(url, { headers: AUTH_HEADER });
  check(res, {
    'browse problems: status 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  sleep(1);
}

export function checkLeaderboard() {
  // Leaderboard is Redis-backed — should handle high concurrency with sub-200ms p(95)
  const res = http.get(
    `${BASE_GAMIFICATION}/api/v1/gamification/leaderboard?page=0&size=20`,
    { headers: AUTH_HEADER }
  );
  check(res, {
    'leaderboard: status 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  sleep(0.5);
}

export function getRecommendations() {
  const res = http.get(`${BASE_AI}/api/v1/recommendations?count=5&type=PROBLEM`, {
    headers: AUTH_HEADER,
  });
  check(res, {
    'AI recommendations: status 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  // AI calls are expensive — sleep longer
  sleep(3);
}

// ---------------------------------------------------------------------------
// k6 scenario configuration
// ---------------------------------------------------------------------------

export const options = {
  scenarios: {
    auth_flow: {
      executor: 'constant-vus',
      vus: 5,
      duration: '5m',
      exec: 'authFlow',
    },
    browse_problems: {
      executor: 'constant-vus',
      vus: 20,
      duration: '5m',
      exec: 'browseProblems',
    },
    leaderboard: {
      executor: 'constant-vus',
      vus: 30,
      duration: '5m',
      exec: 'checkLeaderboard',
    },
    ai_hints: {
      executor: 'constant-vus',
      vus: 5,
      duration: '5m',
      exec: 'getRecommendations',
    },
  },
  thresholds: {
    // Overall platform SLO
    http_req_duration: ['p(95)<1000'],
    http_req_failed:   ['rate<0.01'],
    errors:            ['rate<0.02'],

    // Scenario-specific SLOs
    'http_req_duration{scenario:leaderboard}':    ['p(95)<200'],  // Redis-backed
    'http_req_duration{scenario:ai_hints}':       ['p(95)<5000'], // AI inference
    'http_req_duration{scenario:browse_problems}': ['p(95)<300'],
    'http_req_duration{scenario:auth_flow}':      ['p(95)<500'],
  },
};

// Default export required even when using named scenario exec functions
export default function () {}
