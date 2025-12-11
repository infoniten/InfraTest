import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const dbReadSuccess = new Rate('db_read_success');
const cacheReadSuccess = new Rate('cache_read_success');
const dbReadDuration = new Trend('db_read_duration');
const cacheReadDuration = new Trend('cache_read_duration');
const dbReadCount = new Counter('db_read_count');
const cacheReadCount = new Counter('cache_read_count');

// Combined test: Read load while writes are happening
export let options = {
    scenarios: {
        // Light read load on DB
        db_reads_light: {
            executor: 'constant-vus',
            exec: 'testDbReads',
            vus: 10,
            duration: '5m',
            startTime: '30s', // Start after writes have begun
        },
        // Medium read load on DB
        db_reads_medium: {
            executor: 'constant-vus',
            exec: 'testDbReads',
            vus: 30,
            duration: '3m',
            startTime: '2m',
        },
        // Heavy read load on DB
        db_reads_heavy: {
            executor: 'constant-vus',
            exec: 'testDbReads',
            vus: 50,
            duration: '1m',
            startTime: '3m',
        },
        // Light read load on Cache
        cache_reads_light: {
            executor: 'constant-vus',
            exec: 'testCacheReads',
            vus: 10,
            duration: '5m',
            startTime: '30s',
        },
        // Medium read load on Cache
        cache_reads_medium: {
            executor: 'constant-vus',
            exec: 'testCacheReads',
            vus: 30,
            duration: '3m',
            startTime: '2m',
        },
        // Heavy read load on Cache
        cache_reads_heavy: {
            executor: 'constant-vus',
            exec: 'testCacheReads',
            vus: 50,
            duration: '1m',
            startTime: '3m',
        },
        // Monitor write throughput
        monitor_writes: {
            executor: 'constant-vus',
            exec: 'monitorWriteThroughput',
            vus: 1,
            duration: '5m',
        },
    },
    thresholds: {
        'http_req_duration{operation:db_read}': ['p(95)<500'],
        'http_req_duration{operation:cache_read}': ['p(95)<200'],
        'db_read_success': ['rate>0.5'],
        'cache_read_success': ['rate>0.5'],
    },
};

// Generate trade IDs
function generateTradeIds(count) {
    const tradeIds = [];
    const now = Date.now();

    for (let i = 0; i < count; i++) {
        const timestamp = now - Math.floor(Math.random() * 1800000);
        const randomPart = Math.random().toString(36).substr(2, 9);
        tradeIds.push(`TRD-${timestamp}-${randomPart}`);
    }

    return tradeIds;
}

const tradeIds = generateTradeIds(5000);

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
    dbReadCount.add(1);

    const success = check(res, {
        'DB read success': (r) => r.status === 200 || r.status === 404,
    });

    dbReadSuccess.add(success);

    sleep(Math.random() * 0.2);
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
    cacheReadCount.add(1);

    const success = check(res, {
        'Cache read success': (r) => r.status === 200 || r.status === 404,
    });

    cacheReadSuccess.add(success);

    sleep(Math.random() * 0.1);
}

// Monitor write throughput by checking metrics
export function monitorWriteThroughput() {
    // Check DB consumer metrics
    const dbMetrics = http.get('http://trade-consumer-db-writer:8091/actuator/prometheus');
    if (dbMetrics.status === 200) {
        const body = dbMetrics.body;
        // Extract trades_processed_total metric
        const dbProcessedMatch = body.match(/trades_processed_total{.*consumer="db".*}\s+([\d.]+)/);
        if (dbProcessedMatch) {
            console.log(`DB Trades Processed: ${dbProcessedMatch[1]}`);
        }
    }

    // Check Cache consumer metrics
    const cacheMetrics = http.get('http://trade-consumer-cache-writer:8092/actuator/prometheus');
    if (cacheMetrics.status === 200) {
        const body = cacheMetrics.body;
        const cacheProcessedMatch = body.match(/trades_processed_total{.*consumer="cache".*}\s+([\d.]+)/);
        if (cacheProcessedMatch) {
            console.log(`Cache Trades Processed: ${cacheProcessedMatch[1]}`);
        }
    }

    sleep(10); // Check every 10 seconds
}

export function setup() {
    console.log('=== Starting Combined Read/Write Load Test ===');
    console.log('This test will monitor the impact of read operations on write throughput');
    console.log(`Generated ${tradeIds.length} trade IDs for testing`);

    // Test connectivity
    const dbHealth = http.get('http://trade-consumer-db-reader:8093/actuator/health');
    const cacheHealth = http.get('http://trade-consumer-cache-reader:8094/actuator/health');

    console.log('DB Consumer health:', dbHealth.status);
    console.log('Cache Consumer health:', cacheHealth.status);

    // Get initial metrics
    const dbMetrics = http.get('http://trade-consumer-db-writer:8091/actuator/prometheus');
    const cacheMetrics = http.get('http://trade-consumer-cache-writer:8092/actuator/prometheus');

    console.log('Initial metrics retrieved');
    console.log('=== Test Configuration ===');
    console.log('Phase 1 (0-30s): Warm-up, no read load');
    console.log('Phase 2 (30s-2m): Light read load (10 VUs each)');
    console.log('Phase 3 (2m-3m): Medium read load (30 VUs each)');
    console.log('Phase 4 (3m-4m): Heavy read load (50 VUs each)');
    console.log('Phase 5 (4m-5m): Back to light load');
    console.log('========================');

    return {
        startTime: new Date().toISOString(),
    };
}

export function teardown(data) {
    console.log('=== Combined Load Test Completed ===');
    console.log('Started at:', data.startTime);
    console.log('Ended at:', new Date().toISOString());

    // Get final metrics
    const dbMetrics = http.get('http://trade-consumer-db-writer:8091/actuator/prometheus');
    const cacheMetrics = http.get('http://trade-consumer-cache-writer:8092/actuator/prometheus');

    console.log('Final metrics retrieved');
    console.log('Check Grafana TPS Dashboard for detailed impact analysis');
}
