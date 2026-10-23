import http from 'k6/http';
import { check } from 'k6';

// 테스트 API목록
const paths = {
    inquiries: '/seller/inquiries',
    dashboard: '/seller/dashboard',
    orders: '/seller/orders',
};

// 실행시 주입값
const target = __ENV.TARGET;
const rate = Number(__ENV.RATE);
const minItems = Number(__ENV.MIN_ITEMS || 1000);
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.SELLER_TOKEN || 'local-seller-token';

export const options = {
    scenarios: {
        seller_query: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration: __ENV.DURATION || '2m',
            preAllocatedVUs: 20,
            maxVUs: 100,
        },
    },
    thresholds: {
        checks: ['rate==1'],
    },
};

export default function () {
    const response = http.get(`${baseUrl}${paths[target]}`, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });

    let body = null;
    if (response.status === 200) {
        try {
            body = response.json();
        } catch (_) {
            // JSON이 아니면 아래 응답 검증에서 실패
        }
    }

    const validData =
        body !== null &&
        body.success === true &&
        (target === 'dashboard'
            ? body.data !== null &&
            typeof body.data === 'object' &&
            !Array.isArray(body.data)
            : Array.isArray(body.data) && body.data.length >= minItems);

    check(response, {
        'HTTP 200': (r) => r.status === 200,
        'expected response data': () => validData,
    });
}