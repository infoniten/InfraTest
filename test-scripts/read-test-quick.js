import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const dbReadSuccess = new Rate('db_read_success');
const cacheReadSuccess = new Rate('cache_read_success');
const dbReadDuration = new Trend('db_read_duration');
const cacheReadDuration = new Trend('cache_read_duration');

// Quick test configuration
export let options = {
    scenarios: {
        db_reads: {
            executor: 'constant-vus',
            exec: 'testDbReads',
            vus: 20,
            duration: '1m',
        },
        cache_reads: {
            executor: 'constant-vus',
            exec: 'testCacheReads',
            vus: 20,
            duration: '1m',
        },
    },
    thresholds: {
        'http_req_duration{operation:db_read}': ['p(95)<200'],
        'http_req_duration{operation:cache_read}': ['p(95)<100'],
        'db_read_success': ['rate>0.5'],  // More lenient for quick test
        'cache_read_success': ['rate>0.5'],
    },
};

// Generate a smaller pool of trade IDs for quick testing
function generateTradeIds(count) {
    const tradeIds = [];
    const now = Date.now();

    for (let i = 0; i < count; i++) {
        const timestamp = now - Math.floor(Math.random() * 1800000); // Last 30 minutes
        const randomPart = Math.random().toString(36).substr(2, 9);
        tradeIds.push(`TRD-${timestamp}-${randomPart}`);
    }

    return tradeIds;
}

const tradeIds = generateTradeIds(1000);

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

    dbReadDuration.add(duration);

    const success = check(res, {
        'DB read status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    dbReadSuccess.add(success);

    sleep(0.1);
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

    cacheReadDuration.add(duration);

    const success = check(res, {
        'Cache read status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    cacheReadSuccess.add(success);

    sleep(0.05);
}

export function setup() {
    console.log('Starting quick read test...');

    // Test connectivity
    const dbHealth = http.get('http://trade-consumer-db-reader:8093/actuator/health');
    const cacheHealth = http.get('http://trade-consumer-cache-reader:8094/actuator/health');

    console.log('DB Consumer health:', dbHealth.status);
    console.log('Cache Consumer health:', cacheHealth.status);

    // Test a single read from each service
    const testTradeId = tradeIds[0];
    console.log(`Testing with trade ID: ${testTradeId}`);

    const dbTest = http.get(`http://trade-consumer-db-reader:8093/api/trades/${testTradeId}`);
    console.log('DB test response:', dbTest.status);

    const cacheTest = http.get(`http://trade-consumer-cache-reader:8094/api/trades/${testTradeId}`);
    console.log('Cache test response:', cacheTest.status);
}

export function teardown() {
    console.log('Quick read test completed');
}
