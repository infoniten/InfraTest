import {
  Writer,
  SchemaRegistry,
  SCHEMA_TYPE_STRING,
} from "k6/x/kafka";
import { randomIntBetween, randomItem } from "https://jslib.k6.io/k6-utils/1.2.0/index.js";
import encoding from 'k6/encoding';

// Configuration from environment variables
const KAFKA_BROKERS = __ENV.KAFKA_BROKERS || "kafka:29092";
const KAFKA_TOPIC = __ENV.KAFKA_TOPIC || "trades";
const TRADES_PER_SECOND = parseInt(__ENV.TRADES_PER_SECOND || "700");
const DURATION_SECONDS = parseInt(__ENV.DURATION_SECONDS || "60");

// Sample data for generation
const INSTRUMENTS = [
  { symbol: "USD/RUB", type: "FX_SPOT", exchange: "MOEX" },
  { symbol: "EUR/USD", type: "FX_SPOT", exchange: "MOEX" },
  { symbol: "GBP/USD", type: "FX_SPOT", exchange: "MOEX" },
  { symbol: "USD/JPY", type: "FX_SPOT", exchange: "MOEX" },
  { symbol: "BTC/USD", type: "CRYPTO", exchange: "BINANCE" },
  { symbol: "ETH/USD", type: "CRYPTO", exchange: "BINANCE" },
  { symbol: "SBER", type: "EQUITY", exchange: "MOEX" },
  { symbol: "GAZP", type: "EQUITY", exchange: "MOEX" },
  { symbol: "YNDX", type: "EQUITY", exchange: "MOEX" },
  { symbol: "ROSN", type: "EQUITY", exchange: "MOEX" }
];

const SIDES = ["BUY", "SELL"];
const ORDER_TYPES = ["LIMIT", "MARKET", "STOP", "STOP_LIMIT"];
const CLIENT_IDS = Array.from({ length: 100 }, (_, i) => `CLI-${String(i + 1).padStart(9, '0')}`);
const COUNTERPARTY_IDS = Array.from({ length: 20 }, (_, i) => `CP-${String(i + 1).padStart(9, '0')}`);
const BROKER_NAMES = ["ABC", "XYZ", "DEF", "GHI"];

// Create Kafka writer
const writer = new Writer({
  brokers: [KAFKA_BROKERS],
  topic: KAFKA_TOPIC,
  autoCreateTopic: false,
  compression: "none",
});

// K6 test configuration
export const options = {
  scenarios: {
    kafka_producer: {
      executor: "constant-arrival-rate",
      rate: TRADES_PER_SECOND,
      timeUnit: "1s",
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
};

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function generateTradeId() {
  const now = new Date();
  const dateStr = now.getFullYear().toString() +
                  String(now.getMonth() + 1).padStart(2, '0') +
                  String(now.getDate()).padStart(2, '0');
  const uuid = generateUUID().replace(/-/g, '').substring(0, 12).toUpperCase();
  return `TRD-${dateStr}-${uuid}`;
}

function generateTrade() {
  const instrument = randomItem(INSTRUMENTS);
  const side = randomItem(SIDES);
  const quantity = Math.round((Math.random() * (1000000 - 1000) + 1000) * 100) / 100;

  // Generate price based on instrument type
  let price;
  if (instrument.type === "FX_SPOT") {
    if (instrument.symbol.includes("RUB")) {
      price = Math.round((Math.random() * (95 - 85) + 85) * 10000) / 10000;
    } else {
      price = Math.round((Math.random() * (1.5 - 0.8) + 0.8) * 10000) / 10000;
    }
  } else if (instrument.type === "CRYPTO") {
    if (instrument.symbol.includes("BTC")) {
      price = Math.round((Math.random() * (70000 - 40000) + 40000) * 100) / 100;
    } else {
      price = Math.round((Math.random() * (4000 - 2000) + 2000) * 100) / 100;
    }
  } else { // EQUITY
    price = Math.round((Math.random() * (500 - 100) + 100) * 100) / 100;
  }

  const amount = Math.round(quantity * price * 100) / 100;
  const commission = Math.round(amount * 0.002 * 100) / 100;

  const tradeId = generateTradeId();
  const exchangeTradeId = `EXCH-${instrument.exchange}-${generateUUID().replace(/-/g, '').substring(0, 16).toUpperCase()}`;
  const timestamp = new Date().toISOString();

  return {
    tradeId: tradeId,
    exchangeTradeId: exchangeTradeId,
    timestamp: timestamp,
    executionTime: timestamp,
    instrument: {
      symbol: instrument.symbol,
      type: instrument.type,
      exchange: instrument.exchange,
      currency: instrument.symbol.includes("USD") ? "USD" : "RUB"
    },
    side: side,
    orderType: randomItem(ORDER_TYPES),
    quantity: quantity,
    price: price,
    amount: amount,
    commission: {
      value: commission,
      currency: "USD",
      type: Math.random() > 0.5 ? "MAKER" : "TAKER"
    },
    counterparty: {
      id: randomItem(COUNTERPARTY_IDS),
      name: `BROKER_${randomItem(BROKER_NAMES)}`,
      lei: `549300${generateUUID().replace(/-/g, '').substring(0, 12).toUpperCase()}`
    },
    client: {
      id: randomItem(CLIENT_IDS),
      account: `ACC-${String(randomIntBetween(1, 1000)).padStart(3, '0')}`,
      type: randomItem(["INSTITUTIONAL", "RETAIL", "PROPRIETARY"])
    },
    settlement: {
      date: new Date().toISOString().split('T')[0],
      type: "T+2",
      status: "PENDING"
    },
    venue: {
      mic: instrument.exchange === "MOEX" ? "MISX" : "XNAS",
      segment: randomItem(["MAIN", "DARK", "AUCTION"]),
      session: "MAIN"
    },
    fees: [
      {
        type: "EXCHANGE_FEE",
        value: Math.round((Math.random() * (20 - 5) + 5) * 100) / 100,
        currency: "USD"
      },
      {
        type: "CLEARING_FEE",
        value: Math.round((Math.random() * (10 - 2) + 2) * 100) / 100,
        currency: "USD"
      }
    ],
    metadata: {
      source: randomItem(["FIX", "REST", "WS"]),
      version: "1.0",
      sequenceNumber: randomIntBetween(1000000, 99999999),
      matchingEngine: `ME${String(randomIntBetween(1, 5)).padStart(2, '0')}`,
      latencyMs: Math.round(Math.random() * 9.5 + 0.5 * 1000) / 1000
    },
    regulatory: {
      reportingRequired: true,
      mifid2: {
        tradingVenue: instrument.exchange.substring(0, 4),
        investmentDecision: randomItem(["ALGO", "HUMAN", "HYBRID"]),
        executionWithin: "FIRM"
      }
    }
  };
}

export default function () {
  const trade = generateTrade();
  const tradeJson = JSON.stringify(trade);
  const tradeBase64 = encoding.b64encode(tradeJson);

  const messages = [
    {
      value: tradeBase64,
    },
  ];

  const error = writer.produce({ messages: messages });
  if (error) {
    console.error("Failed to produce message:", error);
  }
}

export function teardown() {
  writer.close();
}
