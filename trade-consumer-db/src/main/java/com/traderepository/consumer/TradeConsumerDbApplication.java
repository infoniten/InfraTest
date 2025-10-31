package com.traderepository.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeConsumerDbApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeConsumerDbApplication.class, args);
    }
}