package com.traderepository.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeConsumerCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeConsumerCacheApplication.class, args);
    }
}