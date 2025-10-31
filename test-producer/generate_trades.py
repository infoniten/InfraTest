#!/usr/bin/env python3
import json
import random
import time
import uuid
from datetime import datetime, timezone
from kafka import KafkaProducer
import os
import sys

# Configuration
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
KAFKA_TOPIC = os.getenv('KAFKA_TOPIC', 'trades')
TRADES_PER_SECOND = int(os.getenv('TRADES_PER_SECOND', '700'))
DURATION_SECONDS = int(os.getenv('DURATION_SECONDS', '60'))
BATCH_SIZE = int(os.getenv('BATCH_SIZE', '100'))

# Sample data for generation
INSTRUMENTS = [
    {"symbol": "USD/RUB", "type": "FX_SPOT", "exchange": "MOEX"},
    {"symbol": "EUR/USD", "type": "FX_SPOT", "exchange": "MOEX"},
    {"symbol": "GBP/USD", "type": "FX_SPOT", "exchange": "MOEX"},
    {"symbol": "USD/JPY", "type": "FX_SPOT", "exchange": "MOEX"},
    {"symbol": "BTC/USD", "type": "CRYPTO", "exchange": "BINANCE"},
    {"symbol": "ETH/USD", "type": "CRYPTO", "exchange": "BINANCE"},
    {"symbol": "SBER", "type": "EQUITY", "exchange": "MOEX"},
    {"symbol": "GAZP", "type": "EQUITY", "exchange": "MOEX"},
    {"symbol": "YNDX", "type": "EQUITY", "exchange": "MOEX"},
    {"symbol": "ROSN", "type": "EQUITY", "exchange": "MOEX"}
]

SIDES = ["BUY", "SELL"]
ORDER_TYPES = ["LIMIT", "MARKET", "STOP", "STOP_LIMIT"]
CLIENT_IDS = [f"CLI-{str(i).zfill(9)}" for i in range(1, 101)]
COUNTERPARTY_IDS = [f"CP-{str(i).zfill(9)}" for i in range(1, 21)]

def generate_trade():
    """Generate a single trade with random data"""
    instrument = random.choice(INSTRUMENTS)
    side = random.choice(SIDES)
    quantity = round(random.uniform(1000, 1000000), 2)

    # Generate price based on instrument type
    if instrument["type"] == "FX_SPOT":
        if "RUB" in instrument["symbol"]:
            price = round(random.uniform(85, 95), 4)
        else:
            price = round(random.uniform(0.8, 1.5), 4)
    elif instrument["type"] == "CRYPTO":
        if "BTC" in instrument["symbol"]:
            price = round(random.uniform(40000, 70000), 2)
        else:
            price = round(random.uniform(2000, 4000), 2)
    else:  # EQUITY
        price = round(random.uniform(100, 500), 2)

    amount = round(quantity * price, 2)
    commission = round(amount * 0.002, 2)  # 0.2% commission

    trade_id = f"TRD-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:12].upper()}"
    exchange_trade_id = f"EXCH-{instrument['exchange']}-{uuid.uuid4().hex[:16].upper()}"

    trade = {
        "tradeId": trade_id,
        "exchangeTradeId": exchange_trade_id,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "executionTime": datetime.now(timezone.utc).isoformat(),
        "instrument": {
            "symbol": instrument["symbol"],
            "type": instrument["type"],
            "exchange": instrument["exchange"],
            "currency": "USD" if "USD" in instrument["symbol"] else "RUB"
        },
        "side": side,
        "orderType": random.choice(ORDER_TYPES),
        "quantity": quantity,
        "price": price,
        "amount": amount,
        "commission": {
            "value": commission,
            "currency": "USD",
            "type": "MAKER" if random.random() > 0.5 else "TAKER"
        },
        "counterparty": {
            "id": random.choice(COUNTERPARTY_IDS),
            "name": f"BROKER_{random.choice(['ABC', 'XYZ', 'DEF', 'GHI'])}",
            "lei": f"549300{uuid.uuid4().hex[:12].upper()}"
        },
        "client": {
            "id": random.choice(CLIENT_IDS),
            "account": f"ACC-{str(random.randint(1, 1000)).zfill(3)}",
            "type": random.choice(["INSTITUTIONAL", "RETAIL", "PROPRIETARY"])
        },
        "settlement": {
            "date": datetime.now().strftime('%Y-%m-%d'),
            "type": "T+2",
            "status": "PENDING"
        },
        "venue": {
            "mic": "MISX" if instrument["exchange"] == "MOEX" else "XNAS",
            "segment": random.choice(["MAIN", "DARK", "AUCTION"]),
            "session": "MAIN"
        },
        "fees": [
            {
                "type": "EXCHANGE_FEE",
                "value": round(random.uniform(5, 20), 2),
                "currency": "USD"
            },
            {
                "type": "CLEARING_FEE",
                "value": round(random.uniform(2, 10), 2),
                "currency": "USD"
            }
        ],
        "metadata": {
            "source": random.choice(["FIX", "REST", "WS"]),
            "version": "1.0",
            "sequenceNumber": random.randint(1000000, 99999999),
            "matchingEngine": f"ME{str(random.randint(1, 5)).zfill(2)}",
            "latencyMs": round(random.uniform(0.5, 10), 3)
        },
        "regulatory": {
            "reportingRequired": True,
            "mifid2": {
                "tradingVenue": instrument["exchange"][:4],
                "investmentDecision": random.choice(["ALGO", "HUMAN", "HYBRID"]),
                "executionWithin": "FIRM"
            }
        }
    }

    return trade

def main():
    print(f"Connecting to Kafka at {KAFKA_BOOTSTRAP_SERVERS}")

    # Create Kafka producer
    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS.split(','),
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        compression_type=None,  # Disable compression for simplicity
        batch_size=16384,
        linger_ms=10,
        acks=1
    )

    print(f"Starting to send {TRADES_PER_SECOND} trades per second for {DURATION_SECONDS} seconds")
    print(f"Total trades to send: {TRADES_PER_SECOND * DURATION_SECONDS}")

    start_time = time.time()
    trades_sent = 0
    batch = []

    # Calculate sleep time between batches
    batch_interval = BATCH_SIZE / TRADES_PER_SECOND

    try:
        while time.time() - start_time < DURATION_SECONDS:
            batch_start = time.time()

            # Generate batch of trades
            for _ in range(BATCH_SIZE):
                trade = generate_trade()
                future = producer.send(KAFKA_TOPIC, value=trade)
                batch.append(future)
                trades_sent += 1

                if trades_sent % 1000 == 0:
                    elapsed = time.time() - start_time
                    rate = trades_sent / elapsed if elapsed > 0 else 0
                    print(f"Sent {trades_sent} trades, Rate: {rate:.1f} TPS")

            # Wait for batch to complete
            for future in batch:
                try:
                    future.get(timeout=1)
                except Exception as e:
                    print(f"Error sending message: {e}")

            batch.clear()

            # Sleep to maintain target rate
            batch_duration = time.time() - batch_start
            if batch_duration < batch_interval:
                time.sleep(batch_interval - batch_duration)

    except KeyboardInterrupt:
        print("\nInterrupted by user")

    finally:
        # Flush and close producer
        producer.flush()
        producer.close()

        total_time = time.time() - start_time
        actual_rate = trades_sent / total_time if total_time > 0 else 0

        print(f"\n=== Summary ===")
        print(f"Total trades sent: {trades_sent}")
        print(f"Total time: {total_time:.2f} seconds")
        print(f"Actual rate: {actual_rate:.1f} TPS")
        print(f"Target rate: {TRADES_PER_SECOND} TPS")

if __name__ == "__main__":
    main()