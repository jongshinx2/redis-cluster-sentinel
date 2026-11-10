import http from 'k6/http';
import { check } from 'k6';

const totalRate = Number(__ENV.TPS || 3000);
const getRate = Math.floor(totalRate / 2);
const queryRate = totalRate - getRate;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    redis_get: {
      executor: 'constant-arrival-rate',
      exec: 'redisGet',
      rate: getRate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 1000),
      maxVUs: Number(__ENV.MAX_VUS || 5000),
    },
    redis_jsonpath: {
      executor: 'constant-arrival-rate',
      exec: 'redisJsonPath',
      rate: queryRate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 1000),
      maxVUs: Number(__ENV.MAX_VUS || 5000),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    dropped_iterations: ['count==0'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export function redisGet() {
  const response = http.get(`${baseUrl}/api/redis/dataset`, { tags: { api: 'redis-get' } });
  check(response, { 'GET status is 200': (r) => r.status === 200 });
}

export function redisJsonPath() {
  const response = http.post(
    `${baseUrl}/api/redis/dataset/query`,
    null,
    {
      tags: { api: 'redis-jsonpath' },
    },
  );
  check(response, { 'JSONPath status is 200': (r) => r.status === 200 });
}
