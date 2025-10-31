# Prometheus Metrics Available

## Overview
The Trade Repository architecture exposes comprehensive metrics through Prometheus for monitoring the entire trade processing pipeline.

## Service Endpoints
- **DB Consumer**: http://localhost:8091/actuator/prometheus
- **Cache Consumer**: http://localhost:8092/actuator/prometheus
- **Redis Exporter**: http://localhost:9121/metrics
- **PostgreSQL Exporter**: http://localhost:9187/metrics
- **Prometheus UI**: http://localhost:9090
- **Grafana Dashboard**: http://localhost:3000 (admin/admin)

## Available Metrics Categories

### 1. Kafka Consumer Metrics
```
# Consumer record consumption rate
kafka_consumer_records_consumed_total{job="trade-consumer-db"}
kafka_consumer_records_consumed_total{job="trade-consumer-cache"}

# Consumer lag (critical for monitoring backlog)
kafka_consumer_lag_sum{job="trade-consumer-db"}
kafka_consumer_lag_sum{job="trade-consumer-cache"}

# Consumer fetch metrics
kafka_consumer_fetch_manager_records_per_request_avg
kafka_consumer_fetch_manager_bytes_consumed_rate
```

### 2. JVM Metrics
```
# Memory usage
jvm_memory_used_bytes{area="heap"}
jvm_memory_max_bytes{area="heap"}
jvm_memory_committed_bytes{area="heap"}

# Garbage collection
jvm_gc_pause_seconds_count
jvm_gc_pause_seconds_sum
jvm_gc_memory_allocated_bytes_total

# CPU usage
process_cpu_usage
system_cpu_usage
```

### 3. Database Connection Pool (HikariCP)
```
# Connection pool health
hikaricp_connections_active{pool="HikariPool-1"}
hikaricp_connections_idle{pool="HikariPool-1"}
hikaricp_connections_pending{pool="HikariPool-1"}
hikaricp_connections_max{pool="HikariPool-1"}

# Connection timing
hikaricp_connections_acquire_seconds
hikaricp_connections_usage_seconds
```

### 4. Spring Boot Application Metrics
```
# HTTP requests (if any REST endpoints)
http_server_requests_seconds_count
http_server_requests_seconds_sum

# Application info
application_info
application_ready_time_seconds
application_started_time_seconds
```

### 5. System Metrics
```
# Process metrics
process_files_open_files
process_files_max_files
process_start_time_seconds
process_uptime_seconds

# System load
system_load_average_1m
system_cpu_count
```

### 6. Redis Metrics (from Redis Exporter)
```
# Redis connections
redis_connected_clients
redis_blocked_clients

# Redis memory
redis_memory_used_bytes
redis_memory_max_bytes

# Redis operations
redis_commands_total
redis_commands_duration_seconds_total

# Redis keyspace
redis_keyspace_keys{db="db0"}
redis_keyspace_expires{db="db0"}
```

## Key Performance Queries

### Trade Processing Rate
```promql
# Current TPS per consumer
rate(kafka_consumer_records_consumed_total[1m])

# Total system TPS
sum(rate(kafka_consumer_records_consumed_total[1m]))
```

### Consumer Health
```promql
# Consumer lag (should be close to 0 under normal load)
kafka_consumer_lag_sum

# Memory pressure
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# Database connection pool utilization
(hikaricp_connections_active / hikaricp_connections_max) * 100
```

### System Performance
```promql
# CPU usage per service
rate(process_cpu_usage[1m]) * 100

# GC pressure
rate(jvm_gc_pause_seconds_count[1m])

# Redis command rate
rate(redis_commands_total[1m])
```

## Alerting Thresholds (Recommended)

### Critical Alerts
- Consumer lag > 1000 messages
- Memory usage > 85%
- CPU usage > 80% for >5min
- Database connections > 90% of pool

### Warning Alerts
- Consumer lag > 100 messages
- Memory usage > 70%
- GC frequency > 10/minute
- Response time > 500ms

## Dashboard Access
The Grafana dashboard has been created and includes:
- Real-time trade processing rates
- Consumer lag monitoring
- JVM memory and CPU usage
- Database connection pool status
- Redis metrics and command rates

Access at: http://localhost:3000 (admin/admin)