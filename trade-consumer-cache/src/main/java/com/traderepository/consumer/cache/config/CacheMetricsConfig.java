package com.traderepository.consumer.cache.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class CacheMetricsConfig {

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong bufferSize = new AtomicLong(0);

    @Bean
    public Counter tradesProcessedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("trades_processed_total")
                .description("Total number of trades processed by Cache consumer")
                .tag("consumer", "cache")
                .register(meterRegistry);
    }

    @Bean
    public Counter tradesErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("trades_error_total")
                .description("Total number of trade processing errors in Cache consumer")
                .tag("consumer", "cache")
                .register(meterRegistry);
    }

    @Bean
    public Timer batchProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("batch_processing_seconds")
                .description("Time taken to process a batch of trades")
                .tag("consumer", "cache")
                .register(meterRegistry);
    }

    @Bean
    public Gauge bufferSizeGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("buffer_size", bufferSize, AtomicLong::get)
                .description("Current size of the trade buffer")
                .tag("consumer", "cache")
                .register(meterRegistry);
    }

    public AtomicLong getProcessedCount() {
        return processedCount;
    }

    public AtomicLong getErrorCount() {
        return errorCount;
    }

    public AtomicLong getBufferSize() {
        return bufferSize;
    }
}