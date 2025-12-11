# Read Load Generator

HTTP нагрузочный генератор для тестирования влияния операций чтения на производительность записи.

## Описание

Read Load Generator - это Spring Boot приложение, которое непрерывно генерирует HTTP запросы к DB Reader и Cache Reader API для имитации реальной нагрузки на чтение.

## Функции

- **Многопоточная генерация нагрузки**: Независимые потоки для DB и Cache readers
- **Настраиваемая интенсивность**: Конфигурируемое количество потоков и интервалов
- **Метрики Prometheus**: Полный набор метрик для мониторинга
- **Управление через переменные окружения**: Легкая настройка без пересборки

## Архитектура

```
┌────────────────────────┐
│ Read Load Generator    │
│  Port: 8095            │
└───────┬───────┬────────┘
        │       │
        │       │ HTTP GET /api/trades/{id}
        ▼       ▼
┌───────────┐ ┌────────────┐
│DB Reader  │ │Cache Reader│
│Port: 8093 │ │Port: 8094  │
└───────────┘ └────────────┘
```

## Конфигурация

### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| `LOAD_GENERATOR_ENABLED` | Включить/выключить генератор | `true` |
| `LOAD_GENERATOR_DB_READER_URL` | URL DB Reader API | `http://trade-consumer-db-reader:8093` |
| `LOAD_GENERATOR_CACHE_READER_URL` | URL Cache Reader API | `http://trade-consumer-cache-reader:8094` |
| `LOAD_GENERATOR_DB_THREADS` | Количество потоков для DB запросов | `10` |
| `LOAD_GENERATOR_CACHE_THREADS` | Количество потоков для Cache запросов | `20` |
| `LOAD_GENERATOR_INTERVAL_MS` | Интервал между запросами (мс) | `100` |

### Расчет нагрузки

**DB Reader TPS** = `DB_THREADS × (1000 / INTERVAL_MS)` = 10 × (1000 / 100) = **~100 TPS**

**Cache Reader TPS** = `CACHE_THREADS × (1000 / INTERVAL_MS)` = 20 × (1000 / 50) = **~400 TPS**

## Запуск

### С Docker Compose

```bash
# Запустить read-load-generator
docker-compose --profile test up -d read-load-generator

# Остановить read-load-generator
docker-compose stop read-load-generator

# Просмотр логов
docker-compose logs -f read-load-generator
```

### Изменение нагрузки

Отредактируйте `docker-compose.yml` для изменения параметров нагрузки:

```yaml
read-load-generator:
  environment:
    LOAD_GENERATOR_DB_THREADS: 20      # Увеличить до 20 потоков
    LOAD_GENERATOR_CACHE_THREADS: 40   # Увеличить до 40 потоков
    LOAD_GENERATOR_INTERVAL_MS: 50     # Уменьшить интервал до 50мс
```

После изменений перезапустите:
```bash
docker-compose --profile test up -d read-load-generator
```

## Метрики

### Endpoints

- **Health**: `http://localhost:8095/actuator/health`
- **Metrics**: `http://localhost:8095/actuator/metrics`
- **Prometheus**: `http://localhost:8095/actuator/prometheus`

### Доступные метрики

#### Счетчики запросов

```promql
# Всего запросов к DB Reader
read_load_requests_total{target="db"}

# Всего запросов к Cache Reader
read_load_requests_total{target="cache"}

# Успешные запросы (200 OK)
read_load_success_total{target="db|cache"}

# Ошибочные запросы (404, 500, timeout)
read_load_errors_total{target="db|cache"}
```

#### Время ответа

```promql
# Время ответа от DB Reader
read_load_duration_seconds{target="db"}

# Время ответа от Cache Reader
read_load_duration_seconds{target="cache"}
```

### Grafana Dashboard Queries

Добавьте эти запросы в Grafana для мониторинга:

**TPS генератора нагрузки:**
```promql
rate(read_load_requests_total[1m])
```

**Процент ошибок:**
```promql
rate(read_load_errors_total[1m]) / rate(read_load_requests_total[1m]) * 100
```

**Среднее время ответа:**
```promql
rate(read_load_duration_seconds_sum[1m]) / rate(read_load_duration_seconds_count[1m])
```

## Логирование

Генератор логирует статистику каждые 30 секунд:

```
2025-11-05 12:53:05 - Read Load Stats - DB: 2960 requests, Cache: 2103 requests, Total: 5063
2025-11-05 12:53:35 - Read Load Stats - DB: 5920 requests, Cache: 4203 requests, Total: 10123
```

## Примеры использования

### 1. Легкая нагрузка (~50 TPS DB, ~100 TPS Cache)

```yaml
LOAD_GENERATOR_DB_THREADS: 5
LOAD_GENERATOR_CACHE_THREADS: 10
LOAD_GENERATOR_INTERVAL_MS: 100
```

### 2. Средняя нагрузка (~100 TPS DB, ~400 TPS Cache)

```yaml
LOAD_GENERATOR_DB_THREADS: 10
LOAD_GENERATOR_CACHE_THREADS: 20
LOAD_GENERATOR_INTERVAL_MS: 100
```

### 3. Высокая нагрузка (~200 TPS DB, ~800 TPS Cache)

```yaml
LOAD_GENERATOR_DB_THREADS: 20
LOAD_GENERATOR_CACHE_THREADS: 40
LOAD_GENERATOR_INTERVAL_MS: 100
```

### 4. Экстремальная нагрузка (~500 TPS DB, ~2000 TPS Cache)

```yaml
LOAD_GENERATOR_DB_THREADS: 50
LOAD_GENERATOR_CACHE_THREADS: 100
LOAD_GENERATOR_INTERVAL_MS: 100
```

## Измерение влияния на запись

### До запуска генератора

```bash
# Получить TPS записи
curl -s http://localhost:9090/api/v1/query?query=rate(trades_processed_total[1m]) | jq
```

### После запуска генератора

```bash
# Запустить генератор
docker-compose --profile test up -d read-load-generator

# Подождать 2 минуты для стабилизации
sleep 120

# Проверить TPS записи снова
curl -s http://localhost:9090/api/v1/query?query=rate(trades_processed_total[1m]) | jq
```

### Анализ в Grafana

1. Откройте TPS Dashboard: `http://localhost:3000/d/tps-dashboard`
2. Наблюдайте за метриками:
   - Write TPS (DB Writer и Cache Writer)
   - Read TPS (DB Reader и Cache Reader)
   - Response Time для readers
   - CPU и Memory usage

## Troubleshooting

### Генератор не запускается

```bash
# Проверить статус
docker-compose ps read-load-generator

# Проверить логи
docker-compose logs read-load-generator

# Проверить что readers запущены
curl http://localhost:8093/actuator/health
curl http://localhost:8094/actuator/health
```

### Слишком много ошибок

```bash
# Проверить метрики ошибок
curl -s http://localhost:8095/actuator/prometheus | grep read_load_errors

# Большинство ошибок - это 404 (trade ID не найден)
# Это нормально, так как генератор создает случайные ID
```

### Изменить интенсивность на лету

```bash
# Остановить генератор
docker-compose stop read-load-generator

# Изменить конфигурацию в docker-compose.yml

# Перезапустить
docker-compose --profile test up -d read-load-generator
```

## Ресурсы

### CPU и Memory limits

По умолчанию в `docker-compose.yml`:

```yaml
deploy:
  resources:
    limits:
      cpus: '1.0'      # Максимум 1 CPU core
      memory: 512M     # Максимум 512MB RAM
    reservations:
      cpus: '0.25'     # Минимум 0.25 CPU
      memory: 128M     # Минимум 128MB RAM
```

Для высоких нагрузок увеличьте лимиты:

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'      # 2 CPU cores
      memory: 1G       # 1GB RAM
```

## Интеграция с существующими системами

Read Load Generator автоматически интегрируется с:

- **Prometheus**: Метрики доступны на порту 8095
- **Grafana**: Добавьте метрики на существующие дашборды
- **Docker Network**: Находится в одной сети с readers

## Best Practices

1. **Запускайте генератор после producer**: Сначала наполните базу данными
2. **Мониторьте ресурсы**: Следите за CPU/Memory readers и database
3. **Начинайте с малой нагрузки**: Постепенно увеличивайте потоки
4. **Используйте в комбинации с K6**: Для более сложных сценариев
5. **Логируйте метрики**: Сохраняйте результаты тестов для сравнения

## См. также

- [ARCHITECTURE.md](../ARCHITECTURE.md) - Общая архитектура системы
- [READ_API_TESTING.md](../READ_API_TESTING.md) - Документация по тестированию Read API
- [run-read-tests.sh](../run-read-tests.sh) - Скрипт для запуска тестов
