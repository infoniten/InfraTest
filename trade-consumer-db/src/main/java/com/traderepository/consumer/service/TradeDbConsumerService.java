package com.traderepository.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traderepository.consumer.config.MetricsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class TradeDbConsumerService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<TradeMessage> buffer;
    private final Counter tradesProcessedCounter;
    private final Counter tradesErrorCounter;
    private final Timer batchProcessingTimer;
    private final MetricsConfig metricsConfig;

    @Value("${batch.size:500}")
    private int batchSize;

    @Value("${batch.timeout.ms:1000}")
    private long batchTimeoutMs;

    private long lastFlushTime = System.currentTimeMillis();

    @Autowired
    public TradeDbConsumerService(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  Counter tradesProcessedCounter,
                                  Counter tradesErrorCounter,
                                  Timer batchProcessingTimer,
                                  MetricsConfig metricsConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.buffer = new ConcurrentLinkedQueue<>();
        this.tradesProcessedCounter = tradesProcessedCounter;
        this.tradesErrorCounter = tradesErrorCounter;
        this.batchProcessingTimer = batchProcessingTimer;
        this.metricsConfig = metricsConfig;
    }

    @KafkaListener(
            topics = "${kafka.topic:trades}",
            groupId = "${kafka.group.id:trade-db-writer-group}",
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

            TradeMessage trade = new TradeMessage(tradeId, message, acknowledgment);
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
                int inserted = batchInsert(batch);

                // Acknowledge all messages in the batch
                for (Acknowledgment ack : acknowledgments) {
                    ack.acknowledge();
                }

                log.info("Batch inserted {} trades to database. Total processed: {}",
                        inserted, metricsConfig.getProcessedCount().get());
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

    private int batchInsert(List<TradeMessage> trades) {
        String sql = "INSERT INTO trades (id, trade_data, created_at) VALUES (?, ?::jsonb, ?) " +
                    "ON CONFLICT (id) DO NOTHING";

        return jdbcTemplate.batchUpdate(sql,
            trades,
            trades.size(),
            (PreparedStatement ps, TradeMessage trade) -> {
                ps.setString(1, trade.tradeId);
                ps.setString(2, trade.tradeData);
                ps.setTimestamp(3, Timestamp.from(Instant.now()));
            }
        ).length;
    }

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void logMetrics() {
        log.info("Trade DB Consumer Metrics - Processed: {}, Errors: {}, Buffer size: {}",
                metricsConfig.getProcessedCount().get(),
                metricsConfig.getErrorCount().get(),
                buffer.size());
    }

    // Inner class to hold trade message with acknowledgment
    private static class TradeMessage {
        final String tradeId;
        final String tradeData;
        final Acknowledgment acknowledgment;

        TradeMessage(String tradeId, String tradeData, Acknowledgment acknowledgment) {
            this.tradeId = tradeId;
            this.tradeData = tradeData;
            this.acknowledgment = acknowledgment;
        }
    }
}