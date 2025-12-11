import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const dbReadSuccess = new Rate('db_read_success');
const cacheReadSuccess = new Rate('cache_read_success');
const dbReadDuration = new Trend('db_read_duration');
const cacheReadDuration = new Trend('cache_read_duration');
const dbReadRequests = new Counter('db_read_requests');
const cacheReadRequests = new Counter('cache_read_requests');

// Test configuration
export let options = {
    scenarios: {
        // DB Read Load Test
        db_reads: {
            executor: 'ramping-vus',
            exec: 'testDbReads',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // Ramp up to 50 VUs
                { duration: '2m', target: 100 },   // Ramp up to 100 VUs
                { duration: '3m', target: 100 },   // Stay at 100 VUs
                { duration: '1m', target: 200 },   // Ramp up to 200 VUs
                { duration: '2m', target: 200 },   // Stay at 200 VUs
                { duration: '30s', target: 0 },    // Ramp down
            ],
            gracefulRampDown: '30s',
        },
        // Cache Read Load Test
        cache_reads: {
            executor: 'ramping-vus',
            exec: 'testCacheReads',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // Ramp up to 50 VUs
                { duration: '2m', target: 100 },   // Ramp up to 100 VUs
                { duration: '3m', target: 100 },   // Stay at 100 VUs
                { duration: '1m', target: 200 },   // Ramp up to 200 VUs
                { duration: '2m', target: 200 },   // Stay at 200 VUs
                { duration: '30s', target: 0 },    // Ramp down
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        'http_req_duration{operation:db_read}': ['p(95)<100', 'p(99)<500'],
        'http_req_duration{operation:cache_read}': ['p(95)<50', 'p(99)<100'],
        'http_req_failed{operation:db_read}': ['rate<0.1'],
        'http_req_failed{operation:cache_read}': ['rate<0.1'],
        'db_read_success': ['rate>0.9'],
        'cache_read_success': ['rate>0.9'],
    },
};

// Generate a pool of trade IDs that should exist in the system
// These will be cycled through during testing
function generateTradeIds(count) {
    const tradeIds = [];
    const now = Date.now();

    for (let i = 0; i < count; i++) {
        // Generate realistic trade IDs that might be in the system
        const timestamp = now - Math.floor(Math.random() * 3600000); // Last hour
        const randomPart = Math.random().toString(36).substr(2, 9);
        tradeIds.push(`TRD-${timestamp}-${randomPart}`);
    }

    return tradeIds;
}

// Pre-generate trade IDs to use in tests
const tradeIds = generateTradeIds(10000);

// Function to get random trade ID
function getRandomTradeId() {
    return tradeIds[Math.floor(Math.random() * tradeIds.length)];
}

// Test DB reads
export function testDbReads() {
    const tradeId = getRandomTradeId();
    const url = `http://trade-consumer-db-reader:8093/api/trades/${tradeId}`;

    const startTime = Date.now();
    const res = http.get(url, {
        tags: { operation: 'db_read' },
    });
    const duration = Date.now() - startTime;

    dbReadRequests.add(1);
    dbReadDuration.add(duration);

    const success = check(res, {
        'DB read status is 200 or 404': (r) => r.status === 200 || r.status === 404,
        'DB read response time < 200ms': (r) => r.timings.duration < 200,
    });

    dbReadSuccess.add(success);

    if (res.status === 200) {
        check(res, {
            'DB response has tradeId': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.tradeId !== undefined;
                } catch (e) {
                    return false;
                }
            },
            'DB response source is database': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.source === 'database';
                } catch (e) {
                    return false;
                }
            },
        });
    }

    // Small random sleep to simulate realistic load
    sleep(Math.random() * 0.1);
}

// Test Cache reads
export function testCacheReads() {
    const tradeId = getRandomTradeId();
    const url = `http://trade-consumer-cache-reader:8094/api/trades/${tradeId}`;

    const startTime = Date.now();
    const res = http.get(url, {
        tags: { operation: 'cache_read' },
    });
    const duration = Date.now() - startTime;

    cacheReadRequests.add(1);
    cacheReadDuration.add(duration);

    const success = check(res, {
        'Cache read status is 200 or 404': (r) => r.status === 200 || r.status === 404,
        'Cache read response time < 100ms': (r) => r.timings.duration < 100,
    });

    cacheReadSuccess.add(success);

    if (res.status === 200) {
        check(res, {
            'Cache response has tradeId': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.tradeId !== undefined;
                } catch (e) {
                    return false;
                }
            },
            'Cache response source is cache': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.source === 'cache';
                } catch (e) {
                    return false;
                }
            },
        });
    }

    // Small random sleep to simulate realistic load
    sleep(Math.random() * 0.05);
}

// Setup function - runs once before test
export function setup() {
    console.log('Starting read load test...');
    console.log(`Generated ${tradeIds.length} trade IDs for testing`);

    // Test connectivity
    const dbHealth = http.get('http://trade-consumer-db-reader:8093/actuator/health');
    const cacheHealth = http.get('http://trade-consumer-cache-reader:8094/actuator/health');

    console.log('DB Consumer health:', dbHealth.status);
    console.log('Cache Consumer health:', cacheHealth.status);

    return {
        startTime: new Date().toISOString(),
        tradeIdCount: tradeIds.length,
    };
}

// Teardown function - runs once after test
export function teardown(data) {
    console.log('Read load test completed');
    console.log('Started at:', data.startTime);
    console.log('Ended at:', new Date().toISOString());
}
