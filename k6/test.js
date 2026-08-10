import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const USERS = [
  {
    name: 'user7@example.com',
    token: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InVzZXI1QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzYzMjE3ODQsImV4cCI6MTc3NzUzMTM4NH0.7VtWAsM0LN41mG7_xChjL5E9rdxAUDLJR8_TWwznMlQ',
  },
  {
    name: 'user6@example.com',
    token: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InVzZXI0QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzYzMjE3ODEsImV4cCI6MTc3NzUzMTM4MX0.2upb8GJmg7t0ogYIA1K3DR0EWcHgFJ0b4YqyqvKot88',
  },
  {
    name: 'user5@example.com',
    token: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InVzZXIzQGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzYzMjE3NzgsImV4cCI6MTc3NzUzMTM3OH0.QoqpWKtSX9QT0-frGdVaW9XqvYytjYB_Je-Pb4l3Hdc',
  },
  {
    name: 'user4@example.com',
    token: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InVzZXIyQGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzYzMjE3NzUsImV4cCI6MTc3NzUzMTM3NX0.4Ee3iS3orQfB8-tm9e0NzdiF77Y_GumTc30-vg3DVf4',
  },
  {
    name: 'user3@example.com',
    token: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InVzZXIxQGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzYzMjE3NzIsImV4cCI6MTc3NzUzMTM3Mn0.bCGXvwPpZL2unRGHrnkuwfqB_h8qiHIIpoPXn0wmvXU',
  },
];

const userRequestCounters = {
  'user7@example.com': new Counter('user7_requests'),
  'user6@example.com': new Counter('user6_requests'),
  'user5@example.com': new Counter('user5_requests'),
  'user4@example.com': new Counter('user4_requests'),
  'user3@example.com': new Counter('user3_requests'),
};

const userSuccessCounters = {
  'user7@example.com': new Counter('user7_success_requests'),
  'user6@example.com': new Counter('user6_success_requests'),
  'user5@example.com': new Counter('user5_success_requests'),
  'user4@example.com': new Counter('user4_success_requests'),
  'user3@example.com': new Counter('user3_success_requests'),
};

const userCountSumCounters = {
  'user7@example.com': new Counter('user7_requested_entry_count'),
  'user6@example.com': new Counter('user6_requested_entry_count'),
  'user5@example.com': new Counter('user5_requested_entry_count'),
  'user4@example.com': new Counter('user4_requested_entry_count'),
  'user3@example.com': new Counter('user3_requested_entry_count'),
};

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '10s', target: 100 },
    { duration: '10s', target: 300 },
    { duration: '10s', target: 0 },
  ],
};

export default function () {
  const url = 'http://localhost:8080/api/draws/7/entry';
  const user = USERS[(__VU - 1) % USERS.length];
  const count = Math.random() < 0.8 ? 1 : 2;

  const payload = JSON.stringify({ count });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': user.token,
      'Idempotency-Key': `${Date.now()}-${__VU}-${__ITER}`,
    },
  };

  // 유저별 "요청 횟수"
  userRequestCounters[user.name].add(1);

  // 유저별 "요청한 총 count 합"
  userCountSumCounters[user.name].add(count);

  const res = http.post(url, payload, params);

  check(res, {
    'not auth error': (r) => ![401, 403].includes(r.status),
    'not server error': (r) => r.status < 500,
  });

  // 성공한 요청만 따로 카운트
  if (res.status >= 200 && res.status < 300) {
    userSuccessCounters[user.name].add(1);
  }

  if (res.status >= 500) {
    console.log(`user=${user.name}, status=${res.status}, body=${res.body}`);
  }

  sleep(1);
}