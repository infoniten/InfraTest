# Load Testing Guide

Руководство по нагрузочному тестированию Trade Repository с использованием Makefile команд.

## Быстрый старт

```bash
# Запустить всю инфраструктуру
make up

# Запустить оба генератора нагрузки (запись + чтение)
make test-load

# Посмотреть статус генераторов
make test-load-status

# Остановить генераторы
make test-load-stop
```

## Доступные команды

### Основные команды нагрузочного тестирования

#### `make test-load`
Запускает оба генератора нагрузки одновременно:
- **Trade Producer**: 700 TPS запись в Kafka
- **Read Load Generator**: ~100 TPS DB + ~400 TPS Cache

```bash
make test-load
```

**Вывод:**
```
Starting load generators...
1. Starting Trade Producer (700 TPS write load)...
✓ Trade Producer started
2. Starting Read Load Generator (~100 TPS DB + ~400 TPS Cache)...
✓ Read Load Generator started

Load generators are running!
Monitor:
  - Grafana Dashboard: http://localhost:3000/d/tps-dashboard
  - Producer Logs: docker-compose logs -f trade-producer
  - Read Generator Logs: docker-compose logs -f read-load-generator
  - Read Generator Metrics: http://localhost:8095/actuator/prometheus

Stop: make test-load-stop
```

#### `make test-load-stop`
Останавливает все генераторы нагрузки.

```bash
make test-load-stop
```

#### `make test-load-status`
Показывает статус генераторов и автоматически выводит статистику.

```bash
make test-load-status
```

**Вывод:**
```
Load Generator Status:

Trade Producer (Write): ✓ Running
Read Load Generator:    ✓ Running

Load Generator Statistics:
...
```

#### `make test-load-stats`
Детальная статистика всех генераторов нагрузки.

```bash
make test-load-stats
```

**Показывает:**
- Write Load (Kafka Producer) - количество отправленных сообщений
- Read Load (HTTP Generator) - статистика запросов к DB/Cache
- Write Throughput - количество обработанных сделок
- Read Requests - общее количество read запросов

### Раздельный запуск генераторов

#### `make test-load-write`
Запускает только генератор записи (Trade Producer).

```bash
make test-load-write
```

Полезно для:
- Тестирования только write пути
- Измерения базовой производительности записи
- Наполнения БД данными

#### `make test-load-read`
Запускает только генератор чтения (Read Load Generator).

```bash
make test-load-read
```

Полезно для:
- Тестирования только read пути
- Измерения производительности читающих API
- Проверки кеширования

## Сценарии использования

### Сценарий 1: Базовый тест производительности

```bash
# 1. Запустить инфраструктуру
make up

# 2. Дождаться готовности (15-30 сек)
sleep 30

# 3. Запустить оба генератора
make test-load

# 4. Подождать 2 минуты для стабилизации
sleep 120

# 5. Посмотреть статистику
make test-load-stats

# 6. Открыть Grafana для визуализации
make monitor

# 7. Остановить генераторы
make test-load-stop
```

### Сценарий 2: Тест влияния чтения на запись

```bash
# 1. Запустить только write нагрузку
make test-load-write

# 2. Подождать 1 минуту и замерить baseline TPS
sleep 60
curl -s http://localhost:8091/actuator/prometheus | grep trades_processed_total

# 3. Добавить read нагрузку
make test-load-read

# 4. Подождать 1 минуту
sleep 60

# 5. Замерить TPS с read нагрузкой
curl -s http://localhost:8091/actuator/prometheus | grep trades_processed_total

# 6. Сравнить результаты в Grafana
```

### Сценарий 3: Тест масштабирования readers

```bash
# 1. Запустить базовую нагрузку
make test-load

# 2. Масштабировать DB readers
docker-compose up -d --scale trade-consumer-db-reader=5

# 3. Масштабировать Cache readers
docker-compose up -d --scale trade-consumer-cache-reader=10

# 4. Наблюдать изменение метрик в Grafana
make monitor
```

### Сценарий 4: Длительный стресс-тест

```bash
# 1. Запустить оба генератора
make test-load

# 2. Оставить на 30 минут
sleep 1800

# 3. Проверить стабильность
make test-load-status

# 4. Проверить метрики
make metrics

# 5. Остановить
make test-load-stop
```

## Мониторинг во время тестов

### Grafana Dashboard

```bash
make monitor
# Откроется http://localhost:3000
```

**Основные панели TPS Dashboard:**
- Write TPS (DB Writer) - скорость записи в БД
- Write TPS (Cache Writer) - скорость записи в Cache
- Read TPS (DB Reader) - скорость чтения из БД
- Read TPS (Cache Reader) - скорость чтения из Cache
- Response Time - время ответа readers

### Prometheus Queries

```bash
# Открыть Prometheus
open http://localhost:9090

# Полезные запросы:
# - rate(trades_processed_total[1m])           # Write TPS
# - rate(trades_read_total[1m])                # Read TPS
# - rate(read_load_requests_total[1m])         # Load generator TPS
# - trades_read_duration_seconds                # Read latency
```

### Логи в реальном времени

```bash
# Trade Producer (write)
docker-compose logs -f trade-producer

# Read Load Generator
docker-compose logs -f read-load-generator

# DB Writer
docker-compose logs -f trade-consumer-db-writer

# DB Reader
docker-compose logs -f trade-consumer-db-reader
```

### Метрики через curl

```bash
# DB Writer metrics
curl http://localhost:8091/actuator/prometheus | grep trades_processed

# Read Load Generator metrics
curl http://localhost:8095/actuator/prometheus | grep read_load

# DB Reader metrics
curl http://localhost:8093/actuator/prometheus | grep trades_read
```

## K6 тесты (альтернатива)

Makefile также поддерживает K6 тесты:

```bash
# Быстрый тест (1 минута, 20 VUs)
docker-compose run --rm k6 run /scripts/read-test-quick.js

# Полный тест (9 минут, до 200 VUs)
docker-compose run --rm k6 run /scripts/read-load-test.js

# Комбинированный тест (5 минут)
docker-compose run --rm k6 run /scripts/combined-read-write-test.js
```

## Ожидаемые результаты

### Write Performance (без read нагрузки)

| Метрика | Значение |
|---------|----------|
| DB Writer TPS | 700+ |
| Cache Writer TPS | 700+ |
| Batch Processing Time (DB) | 50-100ms |
| Batch Processing Time (Cache) | 20-50ms |

### Read Performance

| Метрика | DB Reader | Cache Reader |
|---------|-----------|--------------|
| Throughput | ~100 TPS | ~400 TPS |
| p95 Response Time | < 100ms | < 50ms |
| p99 Response Time | < 500ms | < 100ms |

### Impact of Read Load on Write

**Ожидаемое влияние:**
- DB Writer: < 20% снижение при высокой read нагрузке
- Cache Writer: < 5% снижение при высокой read нагрузке

## Настройка интенсивности нагрузки

### Trade Producer

Отредактируйте `docker-compose.yml`:

```yaml
trade-producer:
  environment:
    TRADES_PER_SECOND: 1000  # Увеличить до 1000 TPS
    DURATION_SECONDS: 120    # Работать 2 минуты
```

### Read Load Generator

Отредактируйте `docker-compose.yml`:

```yaml
read-load-generator:
  environment:
    LOAD_GENERATOR_DB_THREADS: 20      # Больше потоков для DB
    LOAD_GENERATOR_CACHE_THREADS: 40   # Больше потоков для Cache
    LOAD_GENERATOR_INTERVAL_MS: 50     # Меньше интервал = выше TPS
```

После изменений:

```bash
# Пересоздать контейнеры
docker-compose --profile test up -d --force-recreate trade-producer read-load-generator

# Или через Makefile
make test-load-stop
docker-compose build read-load-generator  # если нужна пересборка
make test-load
```

## Troubleshooting

### Генераторы не запускаются

```bash
# Проверить логи
docker-compose logs trade-producer
docker-compose logs read-load-generator

# Проверить зависимости
docker-compose ps

# Перезапустить
make test-load-stop
make test-load
```

### Низкий TPS

**Возможные причины:**
1. Недостаточно ресурсов CPU/Memory
2. Медленная БД/Redis
3. Kafka перегружен

**Решения:**
```bash
# Проверить ресурсы
docker stats

# Проверить Kafka lag
make consumer-groups

# Масштабировать consumers
docker-compose up -d --scale trade-consumer-db-writer=3
```

### Высокая latency

**DB Reader:**
```bash
# Проверить индексы в PostgreSQL
make db-shell
\d trades
# Создать индекс: CREATE INDEX idx_trades_id ON trades(id);
```

**Cache Reader:**
```bash
# Проверить Redis память
make redis-cli
INFO memory

# Увеличить maxmemory если нужно
```

## Best Practices

1. **Начинайте с малого**: Сначала запускайте низкую нагрузку
2. **Мониторьте ресурсы**: Следите за CPU, Memory, Disk I/O
3. **Логируйте результаты**: Сохраняйте metrics для сравнения
4. **Используйте Grafana**: Визуальный мониторинг критичен
5. **Тестируйте реалистичные сценарии**: Комбинируйте read/write

## Полезные команды

```bash
# Полный список команд
make help

# Быстрая настройка окружения
make dev-setup

# Проверка здоровья сервисов
make health

# Просмотр метрик
make metrics

# Очистка всего
make clean

# Валидация данных
make validate
```

## См. также

- [ARCHITECTURE.md](ARCHITECTURE.md) - Архитектура системы
- [READ_API_TESTING.md](READ_API_TESTING.md) - Тестирование Read API
- [read-load-generator/README.md](read-load-generator/README.md) - Детали Read Load Generator
- [run-read-tests.sh](run-read-tests.sh) - Интерактивный скрипт тестирования
