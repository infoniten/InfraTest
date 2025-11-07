# Trade Repository Test Architecture

Архитектура для тестирования высоконагруженной системы обработки биржевых сделок с поддержкой 700 TPS.

## Архитектура

```
[Клиенты] → [Kafka Topic] → [Consumer Group 1: Cache Writer] → [Redis]
                         → [Consumer Group 2: DB Writer] → [PostgreSQL]
```

### Компоненты:

- **Kafka**: Брокер сообщений с 15 партициями для масштабирования
- **Redis**: Кеш для быстрого доступа к недавним сделкам (TTL 1 час)
- **PostgreSQL**: База данных как источник правды
- **Java Consumers**: Два независимых сервиса для записи в кеш и БД
- **Kafka UI**: Web-интерфейс для мониторинга Kafka
- **Prometheus + Grafana**: Мониторинг и метрики

**Опционально:**
- **Schema Registry**: Управление схемами данных (отдельный файл docker-compose.schema-registry.yml)

## Быстрый старт

### 1. Установка и запуск

```bash
# Клонировать репозиторий
git clone <repo-url>
cd TradeRepository

# Запустить архитектуру (первый раз)
make dev-setup
```

### 2. Основные команды

```bash
# Запуск всех сервисов
make up

# Остановка
make down

# Проверка статуса
make health

# Просмотр логов
make logs

# Отправить тестовую сделку
make producer-test

# Запустить генераторы нагрузки (write + read)
make test-load

# Остановить генераторы нагрузки
make test-load-stop

# Посмотреть статус и статистику
make test-load-status

# Открыть мониторинг
make monitor

# Проверить метрики
make metrics
```

### 3. Мониторинг

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8080

## Структура данных

### Формат сделки (JSON):
```json
{
  "tradeId": "TRD-2024-001234567890",
  "timestamp": "2024-01-08T14:30:45.123456Z",
  "instrument": {
    "symbol": "USD/RUB",
    "type": "FX_SPOT",
    "exchange": "MOEX"
  },
  "side": "BUY",
  "quantity": 1000000.00,
  "price": 89.7650,
  "client": {
    "id": "CLI-123456789",
    "account": "ACC-001"
  }
}
```

### База данных (PostgreSQL):
```sql
CREATE TABLE trades (
    id VARCHAR(50) PRIMARY KEY,
    trade_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Кеш (Redis):
```
trades:timeline       - Sorted set по времени
trade:{id}            - Hash с данными сделки
client:{id}:trades    - Сделки по клиенту
instrument:{symbol}:trades - Сделки по инструменту
```

## Производительность

### Целевые метрики:
- **Пропускная способность**: 700 TPS
- **Latency записи в кеш**: < 10ms
- **Latency записи в БД**: < 100ms (batch)
- **Cache hit ratio**: > 80%

### Настройки производительности:

#### Kafka:
- 15 партиций для параллелизма
- Compression: snappy
- Batch size: 16KB для producer, 500 записей для DB consumer

#### PostgreSQL:
- HikariCP: 50 connections
- Batch insert: 500 записей
- Отключен synchronous_commit для производительности

#### Redis:
- TTL: 1 час для сделок
- Pipeline для batch операций
- Partitioning по клиентам и инструментам

## Тестирование

### Нагрузочное тестирование

**Новая архитектура**: Раздельные генераторы для записи и чтения.

```bash
# Запустить оба генератора (write + read нагрузка)
make test-load
# - Trade Producer: 700 TPS write в Kafka
# - Read Load Generator: ~100 TPS DB + ~400 TPS Cache

# Запустить только write нагрузку
make test-load-write

# Запустить только read нагрузку
make test-load-read

# Проверить статус генераторов
make test-load-status

# Посмотреть детальную статистику
make test-load-stats

# Остановить генераторы
make test-load-stop
```

**Подробная документация**: См. [LOAD_TESTING.md](LOAD_TESTING.md) для полного руководства по нагрузочному тестированию.

**Старые методы** (все еще доступны):
```bash
# Одноразовая отправка
make producer-batch  # 100 сделок

# K6 тесты
make test-load-quick  # 300 TPS, 30 сек
make test-load-http   # HTTP load test
```

### Проверка данных

```bash
# Счетчики
make db-count     # Количество в БД
make cache-count  # Количество в кеше

# Последние записи
make db-recent    # 10 последних из БД
make cache-recent # 10 последних из кеша

# Консистентность
make validate     # Сравнение DB vs Cache
```

### Подключение к сервисам

```bash
# PostgreSQL
make db-shell
# SELECT * FROM trades LIMIT 10;

# Redis CLI
make redis-cli
# ZREVRANGE trades:timeline 0 9

# Kafka
make consumer-groups  # Статус consumer groups
make describe-topic   # Информация о топике
```

## Мониторинг и отладка

### Логи
```bash
make logs-consumer-db     # Логи DB consumer
make logs-consumer-cache  # Логи Cache consumer
make logs-kafka          # Логи Kafka
```

### Метрики
- **Trade Rate**: Скорость обработки сделок
- **Consumer Lag**: Отставание consumers
- **Error Rate**: Количество ошибок
- **Cache Hit Ratio**: Эффективность кеша
- **DB Connection Pool**: Использование пула соединений

### Troubleshooting

```bash
# Сброс Kafka offsets
make reset-kafka

# Полная очистка
make clean

# Проверка consumer groups
make consumer-groups

# Проверка топиков
make list-topics
```

## Конфигурация

### Переменные окружения:

#### Kafka Producer:
- `TRADES_PER_SECOND=700` - TPS для тестирования
- `DURATION_SECONDS=60` - Длительность теста

#### DB Consumer:
- `BATCH_SIZE=500` - Размер batch для БД
- `BATCH_TIMEOUT_MS=1000` - Таймаут batch
- `CONSUMER_THREADS=10` - Количество consumer threads

#### Cache Consumer:
- `REDIS_TTL_SECONDS=3600` - TTL для кеша
- `CONSUMER_THREADS=10` - Количество consumer threads

## Масштабирование

### Для увеличения производительности:

1. **Kafka**: Увеличить количество партиций и brokers
2. **Consumers**: Увеличить `CONSUMER_THREADS`
3. **PostgreSQL**: Увеличить `BATCH_SIZE` и connection pool
4. **Redis**: Настроить clustering

### Горизонтальное масштабирование:
```bash
# Увеличить consumers
docker-compose up --scale trade-consumer-db=3 --scale trade-consumer-cache=3
```

## Архитектурные решения

### Почему два Consumer Groups?
- **Независимость**: Падение Redis не влияет на запись в БД
- **Производительность**: Cache writer оптимизирован для скорости
- **Надежность**: PostgreSQL остается источником правды

### Стратегия консистентности:
- **Write-Through** для критичных данных
- **Eventual Consistency** между кешем и БД
- **Reconciliation Job** для проверки расхождений

### Отказоустойчивость:
- Kafka replication factor: 1 (для тестов)
- PostgreSQL с ACID гарантиями
- Redis persistence с AOF
- Graceful shutdown для consumers

---

## Примеры использования

### Отправка сделки через Kafka Console Producer:
```bash
docker-compose exec kafka kafka-console-producer --broker-list localhost:9092 --topic trades
{"tradeId":"TEST-001","timestamp":"2024-01-08T12:00:00Z","instrument":{"symbol":"USD/RUB"},"side":"BUY","quantity":1000,"price":89.76}
```

### Чтение из кеша:
```bash
docker-compose exec redis redis-cli
> HGET trade:TEST-001 data
```

### SQL запросы:
```sql
-- Статистика по часам
SELECT DATE_TRUNC('hour', created_at) as hour, COUNT(*)
FROM trades
GROUP BY hour
ORDER BY hour DESC;

-- Топ инструментов
SELECT trade_data->>'instrument'->>'symbol' as symbol, COUNT(*)
FROM trades
GROUP BY symbol
ORDER BY count DESC;
```