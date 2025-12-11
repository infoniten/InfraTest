# Trade Repository - Архитектура системы

## Обзор

Trade Repository - это распределённая система для обработки и хранения торговых сделок с разделением на модули записи и чтения.

## Архитектура

```
                                   ┌─────────────┐
                                   │   Kafka     │
                                   │  (trades)   │
                                   └──────┬──────┘
                                          │
                        ┌─────────────────┴────────────────┐
                        │                                  │
                        ▼                                  ▼
            ┌───────────────────────┐        ┌───────────────────────┐
            │  DB Writer (8091)     │        │ Cache Writer (8092)   │
            │  Kafka Consumer       │        │  Kafka Consumer       │
            └───────────┬───────────┘        └──────────┬────────────┘
                        │                                │
                        ▼                                ▼
                ┌──────────────┐               ┌─────────────┐
                │  PostgreSQL  │               │   Redis     │
                └───────┬──────┘               └──────┬──────┘
                        │                             │
                        ▼                             ▼
            ┌───────────────────────┐        ┌───────────────────────┐
            │  DB Reader (8093)     │        │ Cache Reader (8094)   │
            │  REST API             │        │  REST API             │
            └───────────────────────┘        └───────────────────────┘
```

## Компоненты

### 1. Write Path (Запись)

#### DB Writer (trade-consumer-db-writer)
- **Порт**: 8091
- **Назначение**: Потребление сообщений из Kafka и запись в PostgreSQL
- **Технологии**: Spring Boot, Spring Kafka, JDBC
- **Особенности**:
  - Батчирование записей (batch size: 500)
  - 10 параллельных потоков Kafka consumers
  - Оптимизированные JDBC batch inserts
  - Метрики записи (TPS, ошибки, размер буфера)

#### Cache Writer (trade-consumer-cache-writer)
- **Порт**: 8092
- **Назначение**: Потребление сообщений из Kafka и запись в Redis
- **Технологии**: Spring Boot, Spring Kafka, Spring Data Redis
- **Особенности**:
  - Батчирование с использованием Redis pipelining
  - Множественные индексы (по клиенту, инструменту, времени)
  - TTL для автоматической очистки старых данных
  - Метрики записи (TPS, ошибки, размер кеша)

### 2. Read Path (Чтение)

#### DB Reader (trade-consumer-db-reader)
- **Порт**: 8093
- **Назначение**: REST API для чтения сделок из PostgreSQL
- **Endpoints**:
  - `GET /api/trades/{tradeId}` - получить сделку по ID
  - `GET /api/trades/health` - проверка работоспособности
- **Метрики**:
  - `trades_read_total{consumer="db"}` - количество чтений
  - `trades_read_error_total{consumer="db"}` - ошибки
  - `trades_read_duration_seconds{consumer="db"}` - время ответа

#### Cache Reader (trade-consumer-cache-reader)
- **Порт**: 8094
- **Назначение**: REST API для чтения сделок из Redis
- **Endpoints**:
  - `GET /api/trades/{tradeId}` - получить сделку по ID
  - `GET /api/trades/health` - проверка работоспособности
- **Метрики**:
  - `trades_read_total{consumer="cache"}` - количество чтений
  - `trades_read_error_total{consumer="cache"}` - ошибки
  - `trades_read_duration_seconds{consumer="cache"}` - время ответа

## Преимущества разделения на Read/Write

### 1. Независимое масштабирование
- **Writers**: Масштабируются по нагрузке записи из Kafka
- **Readers**: Масштабируются по нагрузке HTTP запросов

### 2. Изоляция ресурсов
- Чтение не влияет на запись
- Отдельные connection pools для read/write
- Разные лимиты памяти и CPU

### 3. Специализация
- **Writers**: Оптимизированы для batch processing
- **Readers**: Оптимизированы для низкой latency

### 4. Безопасность
- Readers имеют только read-only доступ к БД
- Минимизация blast radius при проблемах

### 5. Простота мониторинга
- Раздельные метрики для read/write операций
- Лучшая диагностика проблем

## Потоки данных

### Write Flow
```
Producer → Kafka → Writer → DB/Cache
                    ↓
                 Metrics
```

### Read Flow
```
Client → Reader API → DB/Cache → Client
           ↓
        Metrics
```

## Конфигурация портов

| Сервис                       | Порт | Назначение          |
|------------------------------|------|---------------------|
| DB Writer                    | 8091 | Actuator, Metrics   |
| Cache Writer                 | 8092 | Actuator, Metrics   |
| DB Reader                    | 8093 | REST API, Metrics   |
| Cache Reader                 | 8094 | REST API, Metrics   |
| PostgreSQL                   | 5432 | Database            |
| Redis                        | 6379 | Cache               |
| Kafka                        | 9092 | Message Broker      |
| Prometheus                   | 9090 | Metrics Collection  |
| Grafana                      | 3000 | Visualization       |

## Метрики и мониторинг

### Write Metrics
- `trades_processed_total{consumer="db|cache"}` - всего записано
- `trades_error_total{consumer="db|cache"}` - ошибки записи
- `batch_processing_seconds{consumer="db|cache"}` - время обработки batch
- `buffer_size{consumer="db|cache"}` - размер буфера

### Read Metrics
- `trades_read_total{consumer="db|cache"}` - всего прочитано
- `trades_read_error_total{consumer="db|cache"}` - ошибки чтения
- `trades_read_duration_seconds{consumer="db|cache"}` - время ответа (histogram)

### Dashboards
- **TPS Dashboard**: Мониторинг скорости записи и чтения
- **Trade Repository Dashboard**: Общий обзор системы
- **Kafka Monitoring Dashboard**: Состояние Kafka

## Производительность

### Expected Performance

#### DB Writer
- **Throughput**: 700+ TPS
- **Batch Size**: 500 сделок
- **Latency**: 50-100ms (batch processing)

#### Cache Writer
- **Throughput**: 700+ TPS
- **Batch Size**: 500 сделок
- **Latency**: 20-50ms (batch processing)

#### DB Reader
- **Throughput**: 200-300 TPS
- **Response Time**: p95 < 100ms, p99 < 500ms
- **Impact on Writers**: < 20% при высокой нагрузке

#### Cache Reader
- **Throughput**: 500+ TPS
- **Response Time**: p95 < 50ms, p99 < 100ms
- **Impact on Writers**: < 5% при высокой нагрузке

## Масштабирование

### Горизонтальное масштабирование

#### Writers
```bash
# Увеличить количество DB Writers
docker-compose up -d --scale trade-consumer-db-writer=3

# Увеличить количество Cache Writers
docker-compose up -d --scale trade-consumer-cache-writer=3
```

#### Readers
```bash
# Добавить Load Balancer перед Readers
# Увеличить количество DB Readers
docker-compose up -d --scale trade-consumer-db-reader=5

# Увеличить количество Cache Readers
docker-compose up -d --scale trade-consumer-cache-reader=10
```

### Вертикальное масштабирование

Увеличить ресурсы в docker-compose.yml:
```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 2G
```

## Failover и отказоустойчивость

### Writers
- Kafka consumer groups обеспечивают rebalancing
- Автоматический restart при сбое (restart: unless-stopped)
- Manual acknowledgment для гарантии доставки

### Readers
- Stateless сервисы, легко перезапускаются
- Load balancer автоматически исключает неработающие инстансы
- Read-only доступ минимизирует риски

## Безопасность

### Network Isolation
- Writers и Readers в одной Docker network
- Внешний доступ только к Readers через API

### Database Access
- Writers: Read/Write доступ
- Readers: Read-only доступ (рекомендуется настроить)

### API Security
Рекомендуется добавить:
- API Gateway с rate limiting
- Authentication/Authorization
- TLS/SSL шифрование

## Развертывание

### Local Development
```bash
# Запуск всех сервисов
docker-compose up -d

# Запуск только Writers
docker-compose up -d trade-consumer-db-writer trade-consumer-cache-writer

# Запуск только Readers
docker-compose up -d trade-consumer-db-reader trade-consumer-cache-reader
```

### Production Considerations
1. Используйте Kubernetes для оркестрации
2. Настройте auto-scaling на основе метрик
3. Добавьте API Gateway
4. Настройте centralized logging (ELK, Loki)
5. Используйте read replicas для PostgreSQL
6. Рассмотрите Redis Cluster для высокой доступности
