import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 예시:
// k6 run -e BASE_URL=http://localhost:8080 -e AUTH_TOKEN="Bearer <access-token>" k6/bookmark-list-comparison.js
// 결과 비교 시에는 두 서버에 같은 옵션을 적용하고 --summary-export로 JSON을 남긴다.

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const authToken = __ENV.AUTH_TOKEN;
const pageSize = Number(__ENV.PAGE_SIZE || 20);
const pageCount = Number(__ENV.PAGE_COUNT || 5);
const vus = Number(__ENV.VUS || 50);
const duration = __ENV.DURATION || '30s';
// draw_metrics 기반 북마크 정렬 경로를 기본으로 측정한다.
const sort = __ENV.SORT || 'BOOKMARK';

const listDuration = new Trend('bookmark_list_duration', true);
const listFailures = new Counter('bookmark_list_failures');

export const options = {
  scenarios: {
    bookmark_list: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  // 페이지를 순환시켜 한 페이지만 반복 조회하는 캐시 효과를 줄인다.
  const page = __ITER % pageCount;
  const headers = {};
  if (authToken) headers.Authorization = authToken;

  const response = http.get(
    `${baseUrl}/api/draws?tab=ALL&sort=${sort}&page=${page}&size=${pageSize}`,
    { headers, tags: { endpoint: 'draw-list' } },
  );

  listDuration.add(response.timings.duration);
  const success = check(response, {
    '목록 조회 성공': (res) => res.status === 200,
    '목록 데이터 반환': (res) => {
      try {
        return Array.isArray(res.json('content'));
      } catch (_) {
        return false;
      }
    },
  });

  if (!success) listFailures.add(1);
  sleep(0.2);
}
