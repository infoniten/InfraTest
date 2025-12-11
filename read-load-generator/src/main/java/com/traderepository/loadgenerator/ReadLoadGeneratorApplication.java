package com.traderepository.loadgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReadLoadGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadLoadGeneratorApplication.class, args);
    }
}
