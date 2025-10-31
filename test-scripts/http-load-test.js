import http from 'k6/http';
import { check, sleep } from 'k6';

// Test configuration
export let options = {
    stages: [
        { duration: '10s', target: 50 },   // Ramp up to 50 VUs
        { duration: '30s', target: 200 },  // Ramp up to 200 VUs
        { duration: '60s', target: 200 },  // Stay at 200 VUs
        { duration: '10s', target: 0 },    // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1000'], // 95% of requests under 1s
        'http_req_failed': ['rate<0.1'],     // Error rate under 10%
    },
};

// Sample instruments for variety
const instruments = [
    'USD/RUB', 'EUR/USD', 'GBP/USD', 'USD/JPY',
    'BTC/USD', 'ETH/USD', 'SBER', 'GAZP'
];

const sides = ['BUY', 'SELL'];

function generateTrade() {
    const now = new Date().toISOString();
    const instrument = instruments[Math.floor(Math.random() * instruments.length)];
    const side = sides[Math.floor(Math.random() * sides.length)];

    return {
        tradeId: `TRD-K6-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        exchangeTradeId: `EX-${Date.now()}`,
        timestamp: now,
        executionTime: now,
        instrument: {
            symbol: instrument,
            type: instrument.includes('/') ? 'FX_SPOT' : 'EQUITY',
            exchange: 'TEST',
            currency: 'USD'
        },
        side: side,
        orderType: 'LIMIT',
        quantity: Math.floor(Math.random() * 10000) + 1000,
        price: Math.random() * 100 + 50,
        amount: Math.random() * 1000000,
        client: {
            id: `CLI-${Math.floor(Math.random() * 100)}`,
            account: `ACC-${Math.floor(Math.random() * 10)}`,
            type: 'INSTITUTIONAL'
        },
        metadata: {
            source: 'K6_HTTP_TEST',
            version: '1.0',
            sequenceNumber: Date.now(),
            latencyMs: Math.random() * 10
        }
    };
}

export default function () {
    // Test Consumer endpoints
    const trade = generateTrade();

    // Check DB Consumer health
    const dbHealthRes = http.get('http://trade-consumer-db:8091/actuator/health');
    check(dbHealthRes, {
        'DB Consumer is healthy': (r) => r.status === 200,
    });

    // Check Cache Consumer health
    const cacheHealthRes = http.get('http://trade-consumer-cache:8092/actuator/health');
    check(cacheHealthRes, {
        'Cache Consumer is healthy': (r) => r.status === 200,
    });

    // Check DB Consumer metrics
    const dbMetricsRes = http.get('http://trade-consumer-db:8091/actuator/prometheus');
    check(dbMetricsRes, {
        'DB Consumer metrics available': (r) => r.status === 200,
    });

    // Check Cache Consumer metrics
    const cacheMetricsRes = http.get('http://trade-consumer-cache:8092/actuator/prometheus');
    check(cacheMetricsRes, {
        'Cache Consumer metrics available': (r) => r.status === 200,
    });

    sleep(0.1); // 100ms delay between requests
}