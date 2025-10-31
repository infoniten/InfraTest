package com.traderepository.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traderepository.consumer.cache.config.CacheMetricsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class TradeCacheConsumerService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter tradesProcessedCounter;
    private final Counter tradesErrorCounter;
    private final Timer batchProcessingTimer;
    private final CacheMetricsConfig metricsConfig;
    private final ConcurrentLinkedQueue<TradeMessage> buffer;

    @Value("${redis.ttl.seconds:3600}")
    private long redisTtlSeconds;

    @Value("${batch.size:500}")
    private int batchSize;

    @Value("${batch.timeout.ms:1000}")
    private long batchTimeoutMs;

    private long lastFlushTime = System.currentTimeMillis();


    @Autowired
    public TradeCacheConsumerService(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    Counter tradesProcessedCounter,
                                    Counter tradesErrorCounter,
                                    Timer batchProcessingTimer,
                                    CacheMetricsConfig metricsConfig) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tradesProcessedCounter = tradesProcessedCounter;
        this.tradesErrorCounter = tradesErrorCounter;
        this.batchProcessingTimer = batchProcessingTimer;
        this.metricsConfig = metricsConfig;
        this.buffer = new ConcurrentLinkedQueue<>();
    }

    @KafkaListener(
            topics = "${kafka.topic:trades}",
            groupId = "${kafka.group.id:trade-cache-writer-group}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${consumer.threads:10}"
    )
    public void consume(String message,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset,
                       Acknowledgment acknowledgment) {
        try {
            JsonNode tradeJson = objectMapper.readTree(message);
            String tradeId = tradeJson.get("tradeId").asText();

            TradeMessage trade = new TradeMessage(tradeId, message, partition, offset, acknowledgment, tradeJson);
            buffer.offer(trade);
            tradesProcessedCounter.increment(1);
            metricsConfig.getProcessedCount().addAndGet(1);

            // Check if we should flush
            if (buffer.size() >= batchSize ||
                System.currentTimeMillis() - lastFlushTime > batchTimeoutMs) {
                flushBuffer();
            }

            log.debug("Buffered trade: {} from partition: {} offset: {}", tradeId, partition, offset);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            tradesErrorCounter.increment();
            metricsConfig.getErrorCount().incrementAndGet();
            // Acknowledge anyway to avoid blocking
            acknowledgment.acknowledge();
        }
    }

    @Scheduled(fixedDelayString = "${batch.timeout.ms:1000}")
    public void scheduledFlush() {
        if (!buffer.isEmpty() && System.currentTimeMillis() - lastFlushTime > batchTimeoutMs) {
            flushBuffer();
        }
    }

    private synchronized void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        List<TradeMessage> batch = new ArrayList<>();
        List<Acknowledgment> acknowledgments = new ArrayList<>();

        // Drain buffer up to batch size
        for (int i = 0; i < batchSize && !buffer.isEmpty(); i++) {
            TradeMessage trade = buffer.poll();
            if (trade != null) {
                batch.add(trade);
                acknowledgments.add(trade.acknowledgment);
            }
        }

        if (!batch.isEmpty()) {
            Timer.Sample sample = Timer.start();
            try {
                batchInsertToRedis(batch);

                // Acknowledge all messages in the batch
                for (Acknowledgment ack : acknowledgments) {
                    ack.acknowledge();
                }

                log.info("Batch inserted {} trades to Redis. Total processed: {}",
                        batch.size(), metricsConfig.getProcessedCount().get());
                lastFlushTime = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("Error during batch insert: {}", e.getMessage(), e);
                tradesErrorCounter.increment(batch.size());
                metricsConfig.getErrorCount().addAndGet(batch.size());

                // Still acknowledge to prevent reprocessing
                for (Acknowledgment ack : acknowledgments) {
                    ack.acknowledge();
                }
            } finally {
                sample.stop(batchProcessingTimer);
                metricsConfig.getBufferSize().set(buffer.size());
            }
        }
    }

    private void batchInsertToRedis(List<TradeMessage> trades) {
        // Use Redis pipelining for batch operations
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (TradeMessage trade : trades) {
                try {
                    long timestamp = Instant.now().toEpochMilli();
                    byte[] tradeIdBytes = trade.tradeId.getBytes();
                    byte[] messageBytes = trade.tradeData.getBytes();

                    // Add to sorted set (timeline)
                    connection.zAdd("trades:timeline".getBytes(), timestamp, tradeIdBytes);

                    // Store trade data as hash
                    String tradeKey = "trade:" + trade.tradeId;
                    byte[] tradeKeyBytes = tradeKey.getBytes();
                    connection.hSet(tradeKeyBytes, "data".getBytes(), messageBytes);
                    connection.hSet(tradeKeyBytes, "timestamp".getBytes(), String.valueOf(timestamp).getBytes());
                    connection.hSet(tradeKeyBytes, "partition".getBytes(), String.valueOf(trade.partition).getBytes());
                    connection.hSet(tradeKeyBytes, "offset".getBytes(), String.valueOf(trade.offset).getBytes());

                    // Set TTL
                    connection.expire(tradeKeyBytes, redisTtlSeconds);

                    // Add to client-specific sorted set if client ID exists
                    JsonNode tradeJson = trade.tradeJson;
                    if (tradeJson.has("client") && tradeJson.get("client").has("id")) {
                        String clientId = tradeJson.get("client").get("id").asText();
                        String clientKey = "client:" + clientId + ":trades";
                        connection.zAdd(clientKey.getBytes(), timestamp, tradeIdBytes);
                        connection.expire(clientKey.getBytes(), redisTtlSeconds);
                    }

                    // Add to instrument-specific sorted set
                    if (tradeJson.has("instrument") && tradeJson.get("instrument").has("symbol")) {
                        String symbol = tradeJson.get("instrument").get("symbol").asText();
                        String symbolKey = "instrument:" + symbol + ":trades";
                        connection.zAdd(symbolKey.getBytes(), timestamp, tradeIdBytes);
                        connection.expire(symbolKey.getBytes(), redisTtlSeconds);
                    }

                    // Update statistics
                    String hourKey = "stats:hourly:" + (timestamp / 3600000);
                    byte[] hourKeyBytes = hourKey.getBytes();
                    connection.hIncrBy(hourKeyBytes, "count".getBytes(), 1);

                    if (tradeJson.has("quantity")) {
                        double quantity = tradeJson.get("quantity").asDouble();
                        connection.hIncrBy(hourKeyBytes, "volume".getBytes(), (long) quantity);
                    }

                    connection.expire(hourKeyBytes, 86400); // 24 hours TTL

                    // Publish to real-time subscribers
                    connection.publish("trades:new".getBytes(), tradeIdBytes);

                } catch (Exception e) {
                    log.error("Error processing trade {} in batch: {}", trade.tradeId, e.getMessage());
                }
            }
            return null;
        });
    }

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void logMetrics() {
        Long cacheSize = redisTemplate.opsForZSet().size("trades:timeline");
        log.info("Trade Cache Consumer Metrics - Processed: {}, Errors: {}, Buffer size: {}, Cache size: {}",
                metricsConfig.getProcessedCount().get(),
                metricsConfig.getErrorCount().get(),
                buffer.size(),
                cacheSize);
        // Update buffer size gauge
        metricsConfig.getBufferSize().set(cacheSize != null ? cacheSize : 0);
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanupOldEntries() {
        try {
            // Remove entries older than TTL from timeline
            long cutoffTime = Instant.now().minusSeconds(redisTtlSeconds).toEpochMilli();
            Long removed = redisTemplate.opsForZSet().removeRangeByScore("trades:timeline", 0, cutoffTime);
            if (removed != null && removed > 0) {
                log.info("Cleaned up {} old entries from timeline", removed);
            }
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }

    public long getProcessedCount() {
        return metricsConfig.getProcessedCount().get();
    }

    public long getErrorCount() {
        return metricsConfig.getErrorCount().get();
    }

    // Inner class to hold trade message with acknowledgment and parsed JSON
    private static class TradeMessage {
        final String tradeId;
        final String tradeData;
        final int partition;
        final long offset;
        final Acknowledgment acknowledgment;
        final JsonNode tradeJson;

        TradeMessage(String tradeId, String tradeData, int partition, long offset,
                    Acknowledgment acknowledgment, JsonNode tradeJson) {
            this.tradeId = tradeId;
            this.tradeData = tradeData;
            this.partition = partition;
            this.offset = offset;
            this.acknowledgment = acknowledgment;
            this.tradeJson = tradeJson;
        }
    }
}