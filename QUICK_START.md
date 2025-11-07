# Quick Start Guide - Trade Repository

## 1️⃣ Запуск системы (первый раз)

```bash
# Полная установка и настройка
make dev-setup
```

Эта команда:
- Собирает все Docker образы
- Запускает всю инфраструктуру (Kafka, PostgreSQL, Redis, Prometheus, Grafana)
- Создает необходимые Kafka топики
- Проверяет здоровье всех сервисов

## 2️⃣ Запуск нагрузочного тестирования

### Запустить оба генератора нагрузки

```bash
make test-load
```

Эта команда запускает:
- **Trade Producer**: 700 TPS запись в Kafka
- **Read Load Generator**: ~100 TPS чтение из DB + ~400 TPS чтение из Cache

### Проверить статус

```bash
make test-load-status
```

Показывает:
- Статус генераторов (Running/Stopped)
- Количество отправленных/прочитанных сделок
- Write/Read throughput

### Остановить генераторы

```bash
make test-load-stop
```

## 3️⃣ Мониторинг

### Открыть все дашборды

```bash
make monitor
```

Откроется Grafana с доступом к:
- **TPS Dashboard**: http://localhost:3000/d/tps-dashboard
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8080

### Просмотр метрик в консоли

```bash
# Общие метрики системы
make metrics

# Детальная статистика генераторов
make test-load-stats
```

## Полезные команды

```bash
# Список всех команд
make help

# Статус контейнеров
make status

# Проверка здоровья
make health
```

## Дополнительная документация

- [README.md](README.md) - Полное описание архитектуры
- [LOAD_TESTING.md](LOAD_TESTING.md) - Руководство по нагрузочному тестированию
- [ARCHITECTURE.md](ARCHITECTURE.md) - Детальная архитектура системы
