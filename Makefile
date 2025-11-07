.PHONY: help up down restart logs clean test-load test-load-stop test-load-status test-load-write test-load-read test-load-stats monitor build status health producer-test

# Colors for output
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m # No Color

# Default target
help: ## Show this help message
	@echo "$(GREEN)Trade Repository Test Architecture$(NC)"
	@echo "$(YELLOW)Available targets:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'

# Infrastructure
up: ## Start all services
	@echo "$(YELLOW)Starting infrastructure...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)Waiting for services to be ready...$(NC)"
	@sleep 10
	@$(MAKE) health
	@echo "$(GREEN)All services are up!$(NC)"

up-with-schema: ## Start all services including Schema Registry
	@echo "$(YELLOW)Starting infrastructure with Schema Registry...$(NC)"
	docker-compose -f docker-compose.yml -f docker-compose.schema-registry.yml up -d
	@echo "$(GREEN)Waiting for services to be ready...$(NC)"
	@sleep 15
	@$(MAKE) health
	@echo "$(GREEN)All services are up!$(NC)"

down: ## Stop all services
	@echo "$(YELLOW)Stopping all services...$(NC)"
	docker-compose down
	docker-compose -f docker-compose.yml -f docker-compose.schema-registry.yml down 2>/dev/null || true
	@echo "$(GREEN)All services stopped$(NC)"

restart: down up ## Restart all services

logs: ## Show logs from all services
	docker-compose logs -f

logs-consumer-db: ## Show logs from DB consumer
	docker-compose logs -f trade-consumer-db

logs-consumer-cache: ## Show logs from cache consumer
	docker-compose logs -f trade-consumer-cache

logs-kafka: ## Show Kafka logs
	docker-compose logs -f kafka

# Build
build: ## Build Java consumer services
	@echo "$(YELLOW)Building Java consumers...$(NC)"
# 	@if command -v mvn >/dev/null 2>&1; then \
# 		echo "Using system Maven..."; \
# 		(cd trade-consumer-db && mvn clean package -DskipTests); \
# 		(cd trade-consumer-cache && mvn clean package -DskipTests); \
# 	else \
# 		echo "Using Maven wrapper..."; \
# 		(cd trade-consumer-db && ./mvnw clean package -DskipTests); \
# 		(cd trade-consumer-cache && ./mvnw clean package -DskipTests); \
# 	fi
	docker-compose build
	@echo "$(GREEN)Build completed$(NC)"

# Status and Health
status: ## Show status of all containers
	@echo "$(YELLOW)Container Status:$(NC)"
	@docker-compose ps

health: ## Check health of all services
	@echo "$(YELLOW)Checking service health...$(NC)"
	@echo -n "PostgreSQL: "
	@docker-compose exec -T postgres pg_isready > /dev/null 2>&1 && echo "$(GREEN)✓ Healthy$(NC)" || echo "$(RED)✗ Unhealthy$(NC)"
	@echo -n "Redis: "
	@docker-compose exec -T redis redis-cli ping > /dev/null 2>&1 && echo "$(GREEN)✓ Healthy$(NC)" || echo "$(RED)✗ Unhealthy$(NC)"
	@echo -n "Kafka: "
	@docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 && echo "$(GREEN)✓ Healthy$(NC)" || echo "$(RED)✗ Unhealthy$(NC)"

# Kafka Management
create-topics: ## Create Kafka topics
	@echo "$(YELLOW)Creating Kafka topics...$(NC)"
	docker-compose exec kafka kafka-topics --create --topic trades --bootstrap-server localhost:9092 --partitions 15 --replication-factor 1 --if-not-exists
	docker-compose exec kafka kafka-topics --create --topic trades-dlq --bootstrap-server localhost:9092 --partitions 5 --replication-factor 1 --if-not-exists
	@echo "$(GREEN)Topics created$(NC)"

list-topics: ## List Kafka topics
	docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

describe-topic: ## Describe trades topic
	docker-compose exec kafka kafka-topics --describe --topic trades --bootstrap-server localhost:9092

consumer-groups: ## Show consumer group status
	docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
	@echo "$(YELLOW)DB Writer Group:$(NC)"
	docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group trade-db-writer-group --describe
	@echo "$(YELLOW)Cache Writer Group:$(NC)"
	docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group trade-cache-writer-group --describe

# Testing
producer-test: ## Send test trades to Kafka (1 trade)
	@echo "$(YELLOW)Sending test trade...$(NC)"
	docker-compose exec kafka bash -c 'echo '"'"'{"tradeId":"TRD-TEST-$$(date +%s)","timestamp":"$$(date -Iseconds)","instrument":{"symbol":"USD/RUB"},"side":"BUY","quantity":1000000,"price":89.765}'"'"' | kafka-console-producer --broker-list localhost:9092 --topic trades'
	@echo "$(GREEN)Test trade sent$(NC)"

producer-batch: ## Send batch of test trades (100 trades)
	@echo "$(YELLOW)Sending 100 test trades...$(NC)"
	docker-compose run --rm trade-producer
	@echo "$(GREEN)Batch sent$(NC)"

test-load: ## Start write and read load generators (trade-producer + read-load-generator-db + read-load-generator-cache)
	@echo "$(YELLOW)Starting load generators...$(NC)"
	@echo "$(YELLOW)1. Starting Trade Producer (6000 TPS write load)...$(NC)"
	docker-compose --profile test up -d trade-producer
	@echo "$(GREEN)✓ Trade Producer started$(NC)"
	@echo "$(YELLOW)2. Starting DB Read Load Generator (~800 TPS)...$(NC)"
	docker-compose --profile test up -d read-load-generator-db
	@echo "$(GREEN)✓ DB Read Load Generator started$(NC)"
	@echo "$(YELLOW)3. Starting Cache Read Load Generator (~1600 TPS)...$(NC)"
	docker-compose --profile test up -d read-load-generator-cache
	@echo "$(GREEN)✓ Cache Read Load Generator started$(NC)"
	@echo ""
	@echo "$(GREEN)Load generators are running!$(NC)"
	@echo "$(YELLOW)Monitor:$(NC)"
	@echo "  - Grafana Dashboard: $(GREEN)http://localhost:3000/d/tps-dashboard$(NC)"
	@echo "  - Producer Logs: $(GREEN)docker-compose logs -f trade-producer$(NC)"
	@echo "  - DB Read Generator Logs: $(GREEN)docker-compose logs -f read-load-generator-db$(NC)"
	@echo "  - Cache Read Generator Logs: $(GREEN)docker-compose logs -f read-load-generator-cache$(NC)"
	@echo "  - DB Read Metrics: $(GREEN)http://localhost:8095/actuator/prometheus$(NC)"
	@echo "  - Cache Read Metrics: $(GREEN)http://localhost:8096/actuator/prometheus$(NC)"
	@echo ""
	@echo "$(YELLOW)Stop:$(NC) make test-load-stop"

test-load-stop: ## Stop all load generators
	@echo "$(YELLOW)Stopping load generators...$(NC)"
	docker-compose stop trade-producer read-load-generator-db read-load-generator-cache 2>/dev/null || true
	@echo "$(GREEN)Load generators stopped$(NC)"

test-load-status: ## Show status of load generators
	@echo "$(YELLOW)Load Generator Status:$(NC)"
	@echo ""
	@echo -n "Trade Producer (Write):     "
	@docker-compose ps trade-producer 2>/dev/null | grep -q "Up" && echo "$(GREEN)✓ Running$(NC)" || echo "$(RED)✗ Stopped$(NC)"
	@echo -n "DB Read Load Generator:     "
	@docker-compose ps read-load-generator-db 2>/dev/null | grep -q "Up" && echo "$(GREEN)✓ Running$(NC)" || echo "$(RED)✗ Stopped$(NC)"
	@echo -n "Cache Read Load Generator:  "
	@docker-compose ps read-load-generator-cache 2>/dev/null | grep -q "Up" && echo "$(GREEN)✓ Running$(NC)" || echo "$(RED)✗ Stopped$(NC)"
	@echo ""
	@$(MAKE) test-load-stats 2>/dev/null || true

test-load-write: ## Start only write load (trade-producer)
	@echo "$(YELLOW)Starting Trade Producer (write load only)...$(NC)"
	docker-compose --profile test up -d trade-producer
	@echo "$(GREEN)✓ Trade Producer started (6000 TPS)$(NC)"
	@echo "Monitor: docker-compose logs -f trade-producer"

test-load-read: ## Start only read load (read-load-generator-db + read-load-generator-cache)
	@echo "$(YELLOW)Starting Read Load Generators (read load only)...$(NC)"
	docker-compose --profile test up -d read-load-generator-db read-load-generator-cache
	@echo "$(GREEN)✓ Read Load Generators started$(NC)"
	@echo "  - DB Reader: ~800 TPS"
	@echo "  - Cache Reader: ~1600 TPS"
	@echo "Monitor:"
	@echo "  - DB Metrics: http://localhost:8095/actuator/prometheus"
	@echo "  - Cache Metrics: http://localhost:8096/actuator/prometheus"

test-load-stats: ## Show load generator statistics
	@echo "$(YELLOW)Load Generator Statistics:$(NC)"
	@echo ""
	@echo "$(GREEN)Write Load (Kafka Producer):$(NC)"
	@docker-compose logs trade-producer 2>/dev/null | grep -E "trades sent|TPS" | tail -n 3 || echo "  Not running or no data"
	@echo ""
	@echo "$(GREEN)DB Read Load (HTTP Generator):$(NC)"
	@docker-compose logs read-load-generator-db 2>/dev/null | grep "Read Load Stats" | tail -n 1 || echo "  Not running or no data"
	@echo ""
	@echo "$(GREEN)Cache Read Load (HTTP Generator):$(NC)"
	@docker-compose logs read-load-generator-cache 2>/dev/null | grep "Read Load Stats" | tail -n 1 || echo "  Not running or no data"
	@echo ""
	@echo "$(GREEN)Write Throughput (DB Writer):$(NC)"
	@curl -s http://localhost:8091/actuator/prometheus 2>/dev/null | grep "trades_processed_total" | grep -v "^#" || echo "  Service not available"
	@echo ""
	@echo "$(GREEN)DB Read Requests (from generator):$(NC)"
	@curl -s http://localhost:8095/actuator/prometheus 2>/dev/null | grep "read_load_requests_total" | grep -v "^#" || echo "  Service not available"
	@echo ""
	@echo "$(GREEN)Cache Read Requests (from generator):$(NC)"
	@curl -s http://localhost:8096/actuator/prometheus 2>/dev/null | grep "read_load_requests_total" | grep -v "^#" || echo "  Service not available"

test-load-quick: ## Run quick k6 Kafka load test (300 TPS for 30 seconds)
	@echo "$(YELLOW)Starting quick k6 Kafka load test: 300 TPS for 30 seconds...$(NC)"
	docker-compose --profile test run --rm k6 run /scripts/load-test-quick.js
	@echo "$(GREEN)Quick k6 Kafka load test completed$(NC)"

test-load-http: ## Run HTTP load test for consumer endpoints
	@echo "$(YELLOW)Starting HTTP load test for consumer endpoints...$(NC)"
	docker-compose --profile test run --rm k6 run /scripts/http-load-test.js
	@echo "$(GREEN)HTTP load test completed$(NC)"

# Database
db-shell: ## Connect to PostgreSQL
	docker-compose exec postgres psql -U postgres -d tradedb

db-count: ## Count trades in database
	@docker-compose exec -T postgres psql -U postgres -d tradedb -c "SELECT COUNT(*) as total_trades FROM trades;"

db-recent: ## Show recent trades from database
	@docker-compose exec -T postgres psql -U postgres -d tradedb -c "SELECT id, substring(trade_data::text, 1, 100) as trade_preview, created_at FROM trades ORDER BY created_at DESC LIMIT 10;"

# Redis
redis-cli: ## Connect to Redis CLI
	docker-compose exec redis redis-cli

cache-count: ## Count trades in cache
	@echo "$(YELLOW)Trades in cache:$(NC)"
	@docker-compose exec -T redis redis-cli ZCARD trades:timeline

cache-recent: ## Show recent trade IDs from cache
	@echo "$(YELLOW)Recent trades in cache:$(NC)"
	@docker-compose exec -T redis redis-cli ZREVRANGE trades:timeline 0 9

cache-info: ## Show Redis info
	docker-compose exec redis redis-cli INFO stats

# Monitoring
monitor: ## Open monitoring dashboards
	@echo "$(YELLOW)Opening monitoring dashboards...$(NC)"
	@echo "Grafana: $(GREEN)http://localhost:3000$(NC) (admin/admin)"
	@echo "Prometheus: $(GREEN)http://localhost:9090$(NC)"
	@echo "Kafka UI: $(GREEN)http://localhost:8080$(NC)"
	@echo "Consumer Metrics:"
	@echo "  - DB Consumer: $(GREEN)http://localhost:8091/actuator/prometheus$(NC)"
	@echo "  - Cache Consumer: $(GREEN)http://localhost:8092/actuator/prometheus$(NC)"
	@echo "Exporters:"
	@echo "  - Redis Exporter: $(GREEN)http://localhost:9121/metrics$(NC)"
	@echo "  - PostgreSQL Exporter: $(GREEN)http://localhost:9187/metrics$(NC)"
	@open http://localhost:3000 2>/dev/null || xdg-open http://localhost:3000 2>/dev/null || echo "Please open http://localhost:3000"

metrics: ## Show current metrics
	@echo "$(YELLOW)Current System Metrics:$(NC)"
	@echo "$(GREEN)PostgreSQL:$(NC)"
	@docker-compose exec -T postgres psql -U postgres -d tradedb -c "SELECT COUNT(*) as total_trades, MAX(created_at) as last_trade FROM trades;"
	@echo "$(GREEN)Redis:$(NC)"
	@docker-compose exec -T redis redis-cli INFO stats | grep instantaneous_ops_per_sec
	@echo "$(GREEN)Kafka Lag:$(NC)"
	@docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group trade-db-writer-group --describe | grep LAG

# Cleanup
clean: ## Clean everything (containers, volumes, build artifacts)
	@echo "$(RED)Removing all containers and volumes...$(NC)"
	docker-compose down -v
	rm -rf trade-consumer-db/target
	rm -rf trade-consumer-cache/target
	@echo "$(GREEN)Cleanup completed$(NC)"

reset-kafka: ## Reset Kafka offsets
	@echo "$(YELLOW)Resetting Kafka consumer offsets...$(NC)"
	docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group trade-db-writer-group --reset-offsets --to-earliest --all-topics --execute
	docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group trade-cache-writer-group --reset-offsets --to-earliest --all-topics --execute
	@echo "$(GREEN)Offsets reset$(NC)"

# Development
dev-setup: ## Initial development setup
	@echo "$(YELLOW)Setting up development environment...$(NC)"
	@$(MAKE) build
	@$(MAKE) up
	@sleep 15
	@$(MAKE) create-topics
	@$(MAKE) health
	@echo "$(GREEN)Development environment ready!$(NC)"
	@echo "$(YELLOW)Next steps:$(NC)"
	@echo "  1. Run 'make producer-test' to send a test trade"
	@echo "  2. Run 'make test-load' to perform load testing"
	@echo "  3. Run 'make monitor' to view dashboards"

# Quick commands
quick-test: dev-setup producer-batch ## Quick setup and test
	@sleep 5
	@$(MAKE) metrics

validate: ## Validate data consistency between DB and Cache
	@echo "$(YELLOW)Validating data consistency...$(NC)"
	@echo "DB Count: $$(docker-compose exec -T postgres psql -U postgres -d tradedb -t -c 'SELECT COUNT(*) FROM trades;')"
	@echo "Cache Count: $$(docker-compose exec -T redis redis-cli ZCARD trades:timeline)"
	@echo "$(YELLOW)Run 'make db-recent' and 'make cache-recent' to compare recent trades$(NC)"