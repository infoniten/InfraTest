import { check } from 'k6';
import {
    Writer,
    SchemaRegistry,
    SCHEMA_TYPE_STRING,
} from 'k6/x/kafka';

// Quick test configuration with rate limiting - max 500 TPS
export let options = {
    scenarios: {
        kafka_quick_load: {
            executor: 'ramping-arrival-rate',
            startRate: 50,         // Start at 50 iterations/s
            timeUnit: '1s',
            preAllocatedVUs: 20,   // Pre-allocated VUs
            maxVUs: 100,           // Maximum VUs to spawn
            stages: [
                { duration: '5s', target: 200 },   // Ramp up to 200 TPS
                { duration: '20s', target: 500 },  // Ramp up to 500 TPS
                { duration: '5s', target: 0 },     // Ramp down
            ],
        },
    },
    thresholds: {
        'kafka_writer_message_count': ['rate>150'],  // At least 150 TPS
        'kafka_writer_error_count': ['count<50'],    // Less than 50 errors
        'iteration_duration': ['p(95)<3000'],        // 95% of iterations under 3s
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
            source: 'K6_QUICK_TEST',
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
                        'test-id': 'k6-quick-test',
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