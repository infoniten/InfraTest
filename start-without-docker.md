# Запуск без Docker (для тестирования Java)

Если Docker не доступен, можно запустить Java приложения локально для тестирования:

## 1. Установка зависимостей

```bash
# Kafka (через Homebrew)
brew install kafka

# PostgreSQL
brew install postgresql
brew services start postgresql

# Redis
brew install redis
brew services start redis
```

## 2. Настройка сервисов

### PostgreSQL
```bash
createdb tradedb
psql tradedb < init-scripts/01-init.sql
```

### Kafka
```bash
# Запуск Zookeeper
zookeeper-server-start /opt/homebrew/etc/kafka/zookeeper.properties &

# Запуск Kafka
kafka-server-start /opt/homebrew/etc/kafka/server.properties &

# Создание топиков
kafka-topics --create --topic trades --bootstrap-server localhost:9092 --partitions 15 --replication-factor 1
```

## 3. Запуск Java приложений

```bash
# DB Consumer (терминал 1)
cd trade-consumer-db
mvn spring-boot:run

# Cache Consumer (терминал 2)
cd trade-consumer-cache
mvn spring-boot:run
```

## 4. Тестирование

```bash
# Отправка тестового сообщения
kafka-console-producer --broker-list localhost:9092 --topic trades
{"tradeId":"TEST-001","timestamp":"2024-01-08T12:00:00Z","instrument":{"symbol":"USD/RUB"},"side":"BUY","quantity":1000,"price":89.76}

# Проверка PostgreSQL
psql tradedb -c "SELECT COUNT(*) FROM trades;"

# Проверка Redis
redis-cli ZCARD trades:timeline
```