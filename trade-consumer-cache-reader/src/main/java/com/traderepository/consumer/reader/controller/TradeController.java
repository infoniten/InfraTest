package com.traderepository.consumer.reader.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter tradesReadCounter;
    private final Counter tradesReadErrorCounter;
    private final Timer readRequestTimer;

    @Autowired
    public TradeController(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        // Read operation metrics
        this.tradesReadCounter = Counter.builder("trades_read_total")
                .description("Total number of trades read from cache")
                .tag("consumer", "cache")
                .tag("operation", "read")
                .register(meterRegistry);

        this.tradesReadErrorCounter = Counter.builder("trades_read_error_total")
                .description("Total number of trade read errors from cache")
                .tag("consumer", "cache")
                .tag("operation", "read")
                .register(meterRegistry);

        this.readRequestTimer = Timer.builder("trades_read_duration_seconds")
                .description("Time taken to read a trade from cache")
                .tag("consumer", "cache")
                .tag("operation", "read")
                .register(meterRegistry);
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<?> getTradeById(@PathVariable String tradeId) {
        Timer.Sample sample = Timer.start();
        try {
            log.info("Fetching trade with ID from cache: {}", tradeId);

            String tradeKey = "trade:" + tradeId;
            Map<Object, Object> tradeData = redisTemplate.opsForHash().entries(tradeKey);

            if (tradeData.isEmpty()) {
                log.info("Trade not found in cache: {}", tradeId);
                tradesReadErrorCounter.increment();
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Trade not found", "tradeId", tradeId));
            }

            String dataJson = (String) tradeData.get("data");
            String timestamp = (String) tradeData.get("timestamp");
            String partition = (String) tradeData.get("partition");
            String offset = (String) tradeData.get("offset");

            JsonNode trade = objectMapper.readTree(dataJson);

            Map<String, Object> response = new HashMap<>();
            response.put("tradeId", tradeId);
            response.put("trade", trade);
            response.put("timestamp", timestamp);
            response.put("partition", partition);
            response.put("offset", offset);
            response.put("source", "cache");

            tradesReadCounter.increment();
            log.info("Successfully fetched trade from cache: {}", tradeId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching trade {} from cache: {}", tradeId, e.getMessage(), e);
            tradesReadErrorCounter.increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", e.getMessage()));
        } finally {
            sample.stop(readRequestTimer);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "trade-consumer-cache-reader",
            "endpoint", "read-api"
        ));
    }
}
