package com.traderepository.loadgenerator.service;

import com.traderepository.loadgenerator.config.LoadGeneratorConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ReadLoadService {

    private static final Logger logger = LoggerFactory.getLogger(ReadLoadService.class);

    private final LoadGeneratorConfig config;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    private ExecutorService dbExecutor;
    private ExecutorService cacheExecutor;
    private List<String> tradeIds;
    private Random random = new Random();

    private Counter dbRequestsCounter;
    private Counter dbSuccessCounter;
    private Counter dbErrorCounter;
    private Counter cacheRequestsCounter;
    private Counter cacheSuccessCounter;
    private Counter cacheErrorCounter;
    private Timer dbRequestTimer;
    private Timer cacheRequestTimer;

    private AtomicLong totalDbRequests = new AtomicLong(0);
    private AtomicLong totalCacheRequests = new AtomicLong(0);

    public ReadLoadService(LoadGeneratorConfig config, RestTemplate restTemplate,
                          MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            logger.info("Read load generator is disabled");
            return;
        }

        logger.info("Initializing Read Load Generator...");
        logger.info("DB Enabled: {}", config.isDbEnabled());
        logger.info("Cache Enabled: {}", config.isCacheEnabled());
        logger.info("DB Reader URL: {}", config.getDbReaderUrl());
        logger.info("Cache Reader URL: {}", config.getCacheReaderUrl());
        logger.info("DB Threads: {}", config.getDbThreads());
        logger.info("Cache Threads: {}", config.getCacheThreads());
        logger.info("Interval: {}ms", config.getIntervalMs());

        // Initialize metrics
        dbRequestsCounter = Counter.builder("read_load_requests_total")
                .tag("target", "db")
                .description("Total read requests to DB")
                .register(meterRegistry);

        dbSuccessCounter = Counter.builder("read_load_success_total")
                .tag("target", "db")
                .description("Successful read requests to DB")
                .register(meterRegistry);

        dbErrorCounter = Counter.builder("read_load_errors_total")
                .tag("target", "db")
                .description("Failed read requests to DB")
                .register(meterRegistry);

        cacheRequestsCounter = Counter.builder("read_load_requests_total")
                .tag("target", "cache")
                .description("Total read requests to Cache")
                .register(meterRegistry);

        cacheSuccessCounter = Counter.builder("read_load_success_total")
                .tag("target", "cache")
                .description("Successful read requests to Cache")
                .register(meterRegistry);

        cacheErrorCounter = Counter.builder("read_load_errors_total")
                .tag("target", "cache")
                .description("Failed read requests to Cache")
                .register(meterRegistry);

        dbRequestTimer = Timer.builder("read_load_duration_seconds")
                .tag("target", "db")
                .description("Read request duration for DB")
                .register(meterRegistry);

        cacheRequestTimer = Timer.builder("read_load_duration_seconds")
                .tag("target", "cache")
                .description("Read request duration for Cache")
                .register(meterRegistry);

        // Fetch trade IDs from database if DB or Cache is enabled
        if (config.isDbEnabled() || config.isCacheEnabled()) {
            tradeIds = fetchTradeIdsFromDatabase();
            logger.info("Loaded {} trade IDs from database", tradeIds.size());

            if (tradeIds.isEmpty()) {
                logger.warn("No trade IDs found in database! Load generator will not work properly.");
            }
        }

        // Initialize thread pools
        if (config.isDbEnabled()) {
            dbExecutor = Executors.newFixedThreadPool(config.getDbThreads());
            logger.info("DB thread pool initialized with {} threads", config.getDbThreads());
        }

        if (config.isCacheEnabled()) {
            cacheExecutor = Executors.newFixedThreadPool(config.getCacheThreads());
            logger.info("Cache thread pool initialized with {} threads", config.getCacheThreads());
        }

        // Start load generation
        startLoadGeneration();

        logger.info("Read Load Generator initialized and started");
    }

    private List<String> fetchTradeIdsFromDatabase() {
        try {
            logger.info("Fetching trade IDs from database...");
            String query = "SELECT id FROM trades ORDER BY created_at DESC LIMIT 10000";
            List<String> ids = jdbcTemplate.queryForList(query, String.class);
            logger.info("Successfully fetched {} trade IDs from database", ids.size());
            return ids;
        } catch (Exception e) {
            logger.error("Failed to fetch trade IDs from database: {}", e.getMessage());
            logger.warn("Falling back to empty list. Load generator will not work properly.");
            return new ArrayList<>();
        }
    }

    @Scheduled(fixedRate = 300000) // Refresh every 5 minutes
    public void refreshTradeIds() {
        if (!config.isEnabled()) {
            return;
        }

        try {
            List<String> newIds = fetchTradeIdsFromDatabase();
            if (!newIds.isEmpty()) {
                tradeIds = newIds;
                logger.info("Refreshed trade IDs: {} IDs now available", tradeIds.size());
            }
        } catch (Exception e) {
            logger.error("Failed to refresh trade IDs: {}", e.getMessage());
        }
    }

    private void startLoadGeneration() {
        // Start DB load threads
        if (config.isDbEnabled() && dbExecutor != null) {
            for (int i = 0; i < config.getDbThreads(); i++) {
                dbExecutor.submit(this::generateDbLoad);
            }
            logger.info("Started {} DB load generation threads", config.getDbThreads());
        }

        // Start Cache load threads
        if (config.isCacheEnabled() && cacheExecutor != null) {
            for (int i = 0; i < config.getCacheThreads(); i++) {
                cacheExecutor.submit(this::generateCacheLoad);
            }
            logger.info("Started {} Cache load generation threads", config.getCacheThreads());
        }
    }

    private void generateDbLoad() {
        while (config.isEnabled()) {
            try {
                String tradeId = getRandomTradeId();
                String url = config.getDbReaderUrl() + "/api/trades/" + tradeId;

                dbRequestsCounter.increment();
                totalDbRequests.incrementAndGet();

                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    sample.stop(dbRequestTimer);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        dbSuccessCounter.increment();
                    } else {
                        dbErrorCounter.increment();
                    }
                } catch (Exception e) {
                    sample.stop(dbRequestTimer);
                    dbErrorCounter.increment();
                    logger.debug("DB read error for {}: {}", tradeId, e.getMessage());
                }

                Thread.sleep(config.getIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void generateCacheLoad() {
        while (config.isEnabled()) {
            try {
                String tradeId = getRandomTradeId();
                String url = config.getCacheReaderUrl() + "/api/trades/" + tradeId;

                cacheRequestsCounter.increment();
                totalCacheRequests.incrementAndGet();

                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    sample.stop(cacheRequestTimer);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        cacheSuccessCounter.increment();
                    } else {
                        cacheErrorCounter.increment();
                    }
                } catch (Exception e) {
                    sample.stop(cacheRequestTimer);
                    cacheErrorCounter.increment();
                    logger.debug("Cache read error for {}: {}", tradeId, e.getMessage());
                }

                Thread.sleep(config.getIntervalMs() / 2); // Cache reads are faster
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String getRandomTradeId() {
        return tradeIds.get(random.nextInt(tradeIds.size()));
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void logStats() {
        if (!config.isEnabled()) {
            return;
        }

        long dbReqs = totalDbRequests.get();
        long cacheReqs = totalCacheRequests.get();

        logger.info("Read Load Stats - DB: {} requests, Cache: {} requests, Total: {}",
                dbReqs, cacheReqs, dbReqs + cacheReqs);
    }
}
