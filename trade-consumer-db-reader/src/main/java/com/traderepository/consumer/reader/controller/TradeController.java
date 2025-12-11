package com.traderepository.consumer.reader.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Counter tradesReadCounter;
    private final Counter tradesReadErrorCounter;
    private final Timer readRequestTimer;

    @Autowired
    public TradeController(JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;

        // Read operation metrics
        this.tradesReadCounter = Counter.builder("trades_read_total")
                .description("Total number of trades read from DB")
                .tag("consumer", "db")
                .tag("operation", "read")
                .register(meterRegistry);

        this.tradesReadErrorCounter = Counter.builder("trades_read_error_total")
                .description("Total number of trade read errors from DB")
                .tag("consumer", "db")
                .tag("operation", "read")
                .register(meterRegistry);

        this.readRequestTimer = Timer.builder("trades_read_duration_seconds")
                .description("Time taken to read a trade from DB")
                .tag("consumer", "db")
                .tag("operation", "read")
                .register(meterRegistry);
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<?> getTradeById(@PathVariable String tradeId) {
        Timer.Sample sample = Timer.start();
        try {
            log.info("Fetching trade with ID: {}", tradeId);

            String sql = "SELECT id, trade_data, created_at FROM trades WHERE id = ?";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, tradeId);

            if (results.isEmpty()) {
                log.info("Trade not found: {}", tradeId);
                tradesReadErrorCounter.increment();
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Trade not found", "tradeId", tradeId));
            }

            Map<String, Object> row = results.get(0);
            Object tradeDataObj = row.get("trade_data");
            String tradeDataJson = tradeDataObj.toString();
            JsonNode tradeData = objectMapper.readTree(tradeDataJson);

            Map<String, Object> response = Map.of(
                "tradeId", row.get("id"),
                "trade", tradeData,
                "createdAt", row.get("created_at"),
                "source", "database"
            );

            tradesReadCounter.increment();
            log.info("Successfully fetched trade: {}", tradeId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching trade {}: {}", tradeId, e.getMessage(), e);
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
            "service", "trade-consumer-db-reader",
            "endpoint", "read-api"
        ));
    }
}
