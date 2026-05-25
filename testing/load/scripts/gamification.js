import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const errorRate = new Rate('errors');
const xpLatency = new Trend('xp_latency');
const streakLatency = new Trend('streak_latency');
const leaderboardLatency = new Trend('leaderboard_latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : {}),
};

export const options = {
  stages: [
    { duration: '1m',  target: 100 }, // ramp to 100 users
    { duration: '3m',  target: 100 }, // hold steady state
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    // Leaderboard is Redis-backed — should be very fast
    http_req_duration:         ['p(95)<200'],
    http_req_failed:           ['rate<0.01'],
    errors:                    ['rate<0.01'],
    xp_latency:                ['p(95)<200'],
    streak_latency:            ['p(95)<200'],
    leaderboard_latency:       ['p(95)<100'], // Redis-backed — tighter threshold
  },
};

export default function () {
  // Use a random UUID to simulate different users requesting their own XP
  const userId = uuidv4();

  // Scenario 1: Get XP for a user
  group('get_xp', () => {
    const res = http.get(`${BASE_URL}/api/v1/gamification/xp/${userId}`, { headers: HEADERS });
    xpLatency.add(res.timings.duration);
    check(res, {
      'get XP: status 200 or 404': (r) => r.status === 200 || r.status === 404,
    }) || errorRate.add(1);
  });

  sleep(0.3);

  // Scenario 2: Get streak for a user
  group('get_streak', () => {
    const res = http.get(`${BASE_URL}/api/v1/gamification/streak/${userId}`, { headers: HEADERS });
    streakLatency.add(res.timings.duration);
    check(res, {
      'get streak: status 200 or 404': (r) => r.status === 200 || r.status === 404,
    }) || errorRate.add(1);
  });

  sleep(0.3);

  // Scenario 3: Get global leaderboard (Redis-backed, should be fast)
  group('get_leaderboard', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/gamification/leaderboard?page=0&size=20`,
      { headers: HEADERS }
    );
    leaderboardLatency.add(res.timings.duration);
    check(res, {
      'leaderboard: status 200': (r) => r.status === 200,
      'leaderboard: has content': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.content !== undefined || Array.isArray(body);
        } catch {
          return false;
        }
      },
    }) || errorRate.add(1);
  });

  sleep(0.5);
}
