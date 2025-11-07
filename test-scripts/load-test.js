import { check } from 'k6';
import {
    Writer,
    Reader,
    Connection,
    SchemaRegistry,
    SCHEMA_TYPE_STRING,
    SCHEMA_TYPE_JSON,
} from 'k6/x/kafka';

// Test configuration with rate limiting
export let options = {
    scenarios: {
        kafka_load: {
            executor: 'ramping-arrival-rate',
            startRate: 100,        // Start at 100 iterations/s
            timeUnit: '1s',
            preAllocatedVUs: 50,   // Pre-allocated VUs
            maxVUs: 200,           // Maximum VUs to spawn
            stages: [
                { duration: '10s', target: 500 },   // Ramp up to 500 TPS
                { duration: '30s', target: 6000 },  // Ramp up to 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '60s', target: 6000 },  // Stay at 1500 TPS
                { duration: '10s', target: 0 },     // Ramp down
            ],
        },
    },
    thresholds: {
        'kafka_writer_message_count': ['rate>1000'], // At least 1000 TPS
        'kafka_writer_error_count': ['count<100'],   // Less than 100 errors
        'iteration_duration': ['p(95)<5000'],        // 95% of iterations under 5s
    },
};

const brokers = ['kafka:29092'];
const topic = 'trades';

const writer = new Writer({
    brokers: brokers,
    topic: topic,
    autoCreateTopic: true,
    compression: 'snappy',
});

const schemaRegistry = new SchemaRegistry();

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
            source: 'K6_TEST',
            version: '1.0',
            sequenceNumber: Date.now(),
            latencyMs: Math.random() * 10
        }
    };
}

export default function () {
    const trade = generateTrade();

    try {
        writer.produce({
            messages: [
                {
                    key: schemaRegistry.serialize({
                        data: trade.tradeId,
                        schemaType: SCHEMA_TYPE_STRING,
                    }),
                    value: schemaRegistry.serialize({
                        data: JSON.stringify(trade),
                        schemaType: SCHEMA_TYPE_STRING,
                    }),
                    headers: {
                        'test-id': 'k6-load-test',
                        'timestamp': Date.now().toString(),
                    },
                },
            ],
        });

        check(null, {
            'message sent successfully': () => true,
        });
    } catch (error) {
        check(null, {
            'message sent successfully': () => false,
        });
        console.error('Failed to produce message:', error);
    }
}

export function teardown() {
    writer.close();
}