# 🚀 Быстрый запуск Trade Repository

## ✅ Исправленные проблемы
- ❌ ~~Maven wrapper ошибки~~ → ✅ Исправлено
- ❌ ~~Docker compilation errors~~ → ✅ Исправлено
- ❌ ~~Pipeline Redis errors~~ → ✅ Исправлено

## 📋 Предварительные требования

1. **Docker/OrbStack** должен быть запущен
2. **Java 17+** (для локальной разработки)
3. **Maven** (опционально, включен в Docker)

## 🎯 Запуск за 3 шага

### 1. Запуск Docker
```bash
# Если используете OrbStack
open -a OrbStack

# Если используете Docker Desktop
open -a "Docker Desktop"
```

### 2. Сборка и запуск архитектуры
```bash
cd /Users/valerijfomin/IdeaProjects/TradeRepository

# Быстрый запуск (рекомендуется)
make build       # Сборка Java приложений
make up          # Запуск всех сервисов
make health      # Проверка здоровья

# Полная настройка (первый раз)
make dev-setup

# Варианты запуска:
make up              # Стандартный (PostgreSQL, Redis, Kafka, Consumers, Мониторинг)
make up-with-schema  # С Schema Registry (если нужен)
```

### 3. Тестирование
```bash
# Отправить тестовую сделку
make producer-test

# Нагрузочное тестирование (700 TPS)
make test-load

# Проверить метрики
make metrics
```

## 📊 Мониторинг

```bash
# Открыть дашборды
make monitor

# Прямые ссылки:
# Grafana:    http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# Kafka UI:   http://localhost:8080
```

## 🔍 Проверка данных

```bash
# Количество сделок
make db-count     # PostgreSQL
make cache-count  # Redis

# Последние сделки
make db-recent    # 10 последних из БД
make cache-recent # 10 последних из кеша

# Валидация консистентности
make validate
```

## 📝 Логи

```bash
# Все логи
make logs

# Конкретные сервисы
make logs-consumer-db
make logs-consumer-cache
make logs-kafka
```

## 🛠️ Troubleshooting

### Проблема: Docker не запущен
```
Error: Cannot connect to the Docker daemon
```
**Решение**: Запустите Docker/OrbStack

### Проблема: Maven wrapper ошибки
```
Error: Could not load wrapper properties
```
**Решение**: Уже исправлено в новой версии

### Проблема: Consumer отстает
```bash
# Проверить lag
make consumer-groups

# Сбросить offsets
make reset-kafka
```

### Проблема: Порты заняты
```bash
# Остановить все
make down

# Проверить порты
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka
```

## 🧪 Варианты тестирования

### 1. Простой тест
```bash
make producer-test    # 1 сделка
```

### 2. Batch тест
```bash
make producer-batch   # 100 сделок
```

### 3. Нагрузочный тест
```bash
make test-load        # 700 TPS, 60 сек
```

### 4. Кастомный тест
```bash
# Python producer
docker-compose run --rm -e TRADES_PER_SECOND=500 -e DURATION_SECONDS=30 trade-producer

# K6 test
docker-compose run --rm k6 run /scripts/load-test.js
```

## 📈 Целевые метрики

- **Пропускная способность**: 700 TPS
- **Cache write latency**: < 10ms
- **DB write latency**: < 100ms (batch)
- **Cache hit ratio**: > 80%
- **Error rate**: < 1%

## 🧹 Очистка

```bash
# Остановить
make down

# Полная очистка
make clean

# Сброс данных
docker-compose down -v
```

---

## ⚡ Быстрые команды

```bash
# Разработка
make dev-setup     # Полная настройка
make quick-test    # Быстрое тестирование

# Эксплуатация
make up            # Запуск
make status        # Статус
make health        # Здоровье
make metrics       # Метрики

# Тестирование
make producer-test # Тест
make test-load     # Нагрузка
make validate      # Проверка

# Мониторинг
make monitor       # Дашборды
make logs          # Логи
```

🎉 **Готово!** Архитектура для 700 TPS работает!