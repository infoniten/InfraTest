# Trade Repository - Read API Load Testing

Этот документ описывает новые REST API для чтения сделок и методологию нагрузочного тестирования.

## Обзор

Добавлены REST API endpoints для чтения сделок по ключу в оба сервиса:
- **trade-consumer-db** (порт 8091): Чтение из PostgreSQL
- **trade-consumer-cache** (порт 8092): Чтение из Redis

Цель: провести нагрузочное тестирование операций чтения и оценить их влияние на скорость обработки входящих данных.

## REST API Endpoints

### 1. Database Consumer (trade-consumer-db)

#### Получение сделки по ID
```bash
GET http://localhost:8091/api/trades/{tradeId}
```

**Пример успешного ответа (200 OK):**
```json
{
  "tradeId": "TRD-1234567890-abc123",
  "trade": {
    "tradeId": "TRD-1234567890-abc123",
    "instrument": {
      "symbol": "EUR/USD",
      "type": "FX_SPOT"
    },
    "side": "BUY",
    "quantity": 1000,
    "price": 1.0850
  },
  "createdAt": "2025-10-31T10:30:00.000Z",
  "source": "database"
}
```

**Ответ если сделка не найдена (404 NOT FOUND):**
```json
{
  "error": "Trade not found",
  "tradeId": "TRD-NOTFOUND"
}
```

#### Проверка работоспособности
```bash
GET http://localhost:8091/api/trades/health
```

### 2. Cache Consumer (trade-consumer-cache)

#### Получение сделки по ID
```bash
GET http://localhost:8092/api/trades/{tradeId}
```

**Пример успешного ответа (200 OK):**
```json
{
  "tradeId": "TRD-1234567890-abc123",
  "trade": {
    "tradeId": "TRD-1234567890-abc123",
    "instrument": {
      "symbol": "EUR/USD",
      "type": "FX_SPOT"
    },
    "side": "BUY",
    "quantity": 1000,
    "price": 1.0850
  },
  "timestamp": "1698753000000",
  "partition": "3",
  "offset": "12345",
  "source": "cache"
}
```

#### Проверка работоспособности
```bash
GET http://localhost:8092/api/trades/health
```

## Метрики

Для операций чтения добавлены следующие метрики Prometheus:

### Метрики DB Consumer
- `trades_read_total{consumer="db"}` - Общее количество операций чтения
- `trades_read_error_total{consumer="db"}` - Количество ошибок при чтении
- `trades_read_duration_seconds{consumer="db"}` - Время ответа (histogram)

### Метрики Cache Consumer
- `trades_read_total{consumer="cache"}` - Общее количество операций чтения
- `trades_read_error_total{consumer="cache"}` - Количество ошибок при чтении
- `trades_read_duration_seconds{consumer="cache"}` - Время ответа (histogram)

### Просмотр метрик
- Prometheus: http://localhost:9090
- Grafana Dashboard: http://localhost:3000 (Dashboard: "Trade Repository - TPS Dashboard")

## K6 Тестовые Скрипты

### 1. Quick Read Test (Быстрый тест)
Простой тест для проверки работоспособности API.

```bash
# Запуск
docker-compose exec k6 k6 run /scripts/read-test-quick.js

# Параметры
# - Длительность: 1 минута
# - Нагрузка: 20 VUs на DB, 20 VUs на Cache
# - Подходит для: Начального тестирования
```

### 2. Read Load Test (Полноценный нагрузочный тест)
Масштабный тест с постепенным увеличением нагрузки.

```bash
# Запуск
docker-compose exec k6 k6 run /scripts/read-load-test.js

# Параметры
# - Длительность: ~9 минут
# - Нагрузка: 0 -> 50 -> 100 -> 200 VUs
# - Подходит для: Определения максимальной производительности
```

### 3. Combined Read/Write Test (Комбинированный тест)
Тест влияния операций чтения на скорость записи.

```bash
# Запуск
docker-compose exec k6 k6 run /scripts/combined-read-write-test.js

# Параметры
# - Длительность: 5 минут
# - Фазы нагрузки:
#   - 0-30s: Разогрев, только запись
#   - 30s-2m: Легкая нагрузка чтения (10 VUs)
#   - 2m-3m: Средняя нагрузка (30 VUs)
#   - 3m-4m: Высокая нагрузка (50 VUs)
#   - 4m-5m: Возврат к легкой нагрузке
# - Подходит для: Оценки влияния чтения на запись
```

## Инструкции по тестированию

### Подготовка

1. **Убедитесь что все сервисы запущены:**
```bash
docker-compose up -d
```

2. **Проверьте работоспособность сервисов:**
```bash
# DB Consumer
curl http://localhost:8091/actuator/health
curl http://localhost:8091/api/trades/health

# Cache Consumer
curl http://localhost:8092/actuator/health
curl http://localhost:8092/api/trades/health
```

3. **Запустите producer для генерации данных:**
```bash
docker-compose --profile test up -d trade-producer
```

4. **Подождите 30-60 секунд для накопления данных**

### Сценарий тестирования

#### Сценарий 1: Базовое тестирование производительности

1. Запустите quick read test:
```bash
docker-compose exec k6 k6 run /scripts/read-test-quick.js
```

2. Откройте Grafana (http://localhost:3000) и посмотрите на дашборд "Trade Repository - TPS Dashboard"

3. Обратите внимание на:
   - Read TPS (Transactions Per Second)
   - Read Response Time (среднее и p95)
   - Влияние на Write TPS

#### Сценарий 2: Полное нагрузочное тестирование

1. Запустите producer для постоянной нагрузки записи:
```bash
# Установите TPS в docker-compose.yml
# TRADES_PER_SECOND: 700
docker-compose --profile test up -d trade-producer
```

2. Запустите combined test:
```bash
docker-compose exec k6 k6 run /scripts/combined-read-write-test.js
```

3. Наблюдайте в Grafana:
   - Как меняется Write TPS при увеличении Read нагрузки
   - Как растёт время ответа при высокой нагрузке
   - Размер буферов и очередей

#### Сценарий 3: Сравнение DB vs Cache

1. Запустите read load test:
```bash
docker-compose exec k6 k6 run /scripts/read-load-test.js
```

2. Сравните метрики:
   - Cache должен быть быстрее (p95 < 50ms vs p95 < 100ms)
   - Cache должен иметь более высокий throughput
   - DB может показывать большее влияние на Write TPS

### Анализ результатов

#### В Grafana Dashboard смотрите:

1. **Trade Read Rate (TPS)**
   - Сколько операций чтения в секунду выполняется
   - Сравнение DB vs Cache

2. **Read Response Time**
   - Среднее время ответа
   - p95 перцентиль (95% запросов быстрее этого времени)
   - Как меняется при увеличении нагрузки

3. **Trade Consumption Rate (TPS)**
   - Основная метрика - как чтение влияет на запись
   - Если TPS падает при увеличении Read нагрузки - есть конкуренция за ресурсы

4. **Buffer Sizes**
   - Рост буфера при высокой Read нагрузке может указывать на замедление

5. **Read Error Rate**
   - Должен быть низким (< 10%)
   - Высокий error rate может указывать на проблемы

#### В консоли K6:

```
✓ DB read success                      95.2% (9520/10000)
✓ Cache read success                   98.5% (9850/10000)

http_req_duration{operation:db_read}
  avg=45ms    p95=89ms    p99=145ms

http_req_duration{operation:cache_read}
  avg=12ms    p95=28ms    p99=52ms
```

## Ожидаемые результаты

### Cache (Redis)
- **Response Time**: p95 < 50ms, p99 < 100ms
- **Throughput**: 500+ TPS на одном инстансе
- **Влияние на Write**: Минимальное (< 5% снижение Write TPS)

### Database (PostgreSQL)
- **Response Time**: p95 < 100ms, p99 < 500ms
- **Throughput**: 200-300 TPS на одном инстансе
- **Влияние на Write**: Умеренное (10-20% снижение Write TPS при высокой нагрузке)

## Рекомендации по оптимизации

Если наблюдается значительное влияние на Write TPS:

1. **Для DB:**
   - Добавьте read replicas для разделения read/write нагрузки
   - Увеличьте размер connection pool
   - Настройте индексы для частых запросов

2. **Для Cache:**
   - Увеличьте max connections в Redis
   - Рассмотрите Redis Cluster для распределения нагрузки
   - Проверьте TTL настройки

3. **Для обоих:**
   - Рассмотрите rate limiting для Read API
   - Добавьте CDN/caching layer перед API
   - Масштабируйте горизонтально (больше инстансов)

## Troubleshooting

### Проблема: API возвращает 404 для всех запросов

**Решение:**
1. Убедитесь что producer запущен и генерирует данные
2. Проверьте что consumer'ы обрабатывают сообщения
3. Подождите 30-60 секунд для накопления данных

### Проблема: Высокий error rate в тестах

**Решение:**
1. Проверьте логи consumer'ов:
```bash
docker-compose logs trade-consumer-db
docker-compose logs trade-consumer-cache
```

2. Проверьте доступность БД и Redis:
```bash
docker-compose ps
```

### Проблема: Низкая производительность

**Решение:**
1. Проверьте ресурсы системы (CPU, RAM, Disk I/O)
2. Увеличьте лимиты в docker-compose.yml
3. Проверьте настройки PostgreSQL и Redis

## Дополнительная информация

- **Логи**: `./trade-consumer-db/logs/` и `./trade-consumer-cache/logs/`
- **Метрики**: http://localhost:9090 (Prometheus)
- **Визуализация**: http://localhost:3000 (Grafana, admin/admin)
- **Kafka UI**: http://localhost:8080

## Контакты и поддержка

Для вопросов и предложений создавайте issue в репозитории проекта.
