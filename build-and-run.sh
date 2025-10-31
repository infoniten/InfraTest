#!/bin/bash
set -e

echo "🔧 Building Java consumers..."

# Check if Maven is installed
if command -v mvn &> /dev/null; then
    echo "Using system Maven..."
    cd trade-consumer-db && mvn clean package -DskipTests && cd ..
    cd trade-consumer-cache && mvn clean package -DskipTests && cd ..
else
    echo "Using Maven wrapper..."
    cd trade-consumer-db && ./mvnw clean package -DskipTests && cd ..
    cd trade-consumer-cache && ./mvnw clean package -DskipTests && cd ..
fi

echo "✅ Build completed!"

echo "🚀 Starting Docker containers..."
docker-compose up -d

echo "⏳ Waiting for services to start..."
sleep 15

echo "📊 Creating Kafka topics..."
docker-compose exec kafka kafka-topics --create --topic trades --bootstrap-server localhost:9092 --partitions 15 --replication-factor 1 --if-not-exists || true
docker-compose exec kafka kafka-topics --create --topic trades-dlq --bootstrap-server localhost:9092 --partitions 5 --replication-factor 1 --if-not-exists || true

echo "🔍 Checking service health..."
echo -n "PostgreSQL: "
docker-compose exec -T postgres pg_isready > /dev/null 2>&1 && echo "✅ Healthy" || echo "❌ Unhealthy"

echo -n "Redis: "
docker-compose exec -T redis redis-cli ping > /dev/null 2>&1 && echo "✅ Healthy" || echo "❌ Unhealthy"

echo -n "Kafka: "
docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 && echo "✅ Healthy" || echo "❌ Unhealthy"

echo ""
echo "🎉 Development environment is ready!"
echo ""
echo "📋 Next steps:"
echo "  🧪 Test: make producer-test"
echo "  🚀 Load test: make test-load"
echo "  📊 Monitoring: make monitor"
echo "  📈 Metrics: make metrics"