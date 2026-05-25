import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const listProblemsLatency = new Trend('list_problems_latency');
const detailLatency = new Trend('problem_detail_latency');
const filterLatency = new Trend('filter_problems_latency');
const searchLatency = new Trend('search_problems_latency');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : {}),
};

export const options = {
  stages: [
    { duration: '1m',  target: 10 },  // ramp up
    { duration: '1m',  target: 50 },  // continue ramp
    { duration: '5m',  target: 50 },  // steady state
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration:    ['p(95)<300'],  // 95% of requests under 300ms
    http_req_failed:      ['rate<0.01'],  // <1% error rate
    errors:               ['rate<0.01'],
    list_problems_latency:['p(95)<300'],
    problem_detail_latency:['p(95)<200'],
    filter_problems_latency:['p(95)<300'],
    search_problems_latency:['p(95)<400'],
  },
};

export default function () {
  // Scenario 1: List problems (default pagination)
  group('list_problems', () => {
    const res = http.get(`${BASE_URL}/api/v1/problems?page=0&size=20`, { headers: HEADERS });
    listProblemsLatency.add(res.timings.duration);
    check(res, {
      'list problems: status 200': (r) => r.status === 200,
      'list problems: has content array': (r) => {
        try {
          const body = JSON.parse(r.body);
          return Array.isArray(body.content);
        } catch {
          return false;
        }
      },
    }) || errorRate.add(1);
  });

  sleep(0.5);

  // Scenario 2: Get a specific problem detail
  group('problem_detail', () => {
    const slugs = ['two-sum', 'add-two-numbers', 'longest-substring-without-repeating-characters'];
    const slug = slugs[Math.floor(Math.random() * slugs.length)];
    const res = http.get(`${BASE_URL}/api/v1/problems/${slug}`, { headers: HEADERS });
    detailLatency.add(res.timings.duration);
    check(res, {
      'problem detail: status 200 or 404': (r) => r.status === 200 || r.status === 404,
    }) || errorRate.add(1);
  });

  sleep(0.5);

  // Scenario 3: Filter by difficulty
  group('filter_by_difficulty', () => {
    const difficulties = ['EASY', 'MEDIUM', 'HARD'];
    const difficulty = difficulties[Math.floor(Math.random() * difficulties.length)];
    const res = http.get(`${BASE_URL}/api/v1/problems?difficulty=${difficulty}&page=0&size=20`, {
      headers: HEADERS,
    });
    filterLatency.add(res.timings.duration);
    check(res, {
      'filter: status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
  });

  sleep(0.5);

  // Scenario 4: Search problems by keyword
  group('search_problems', () => {
    const keywords = ['binary', 'tree', 'graph', 'dynamic', 'sort', 'array', 'string'];
    const keyword = keywords[Math.floor(Math.random() * keywords.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/problems?search=${encodeURIComponent(keyword)}&page=0&size=20`,
      { headers: HEADERS }
    );
    searchLatency.add(res.timings.duration);
    check(res, {
      'search: status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
  });

  sleep(1);
}
