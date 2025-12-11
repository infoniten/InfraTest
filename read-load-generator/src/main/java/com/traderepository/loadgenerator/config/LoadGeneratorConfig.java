package com.traderepository.loadgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "load.generator")
public class LoadGeneratorConfig {

    private String dbReaderUrl = "http://trade-consumer-db-reader:8093";
    private String cacheReaderUrl = "http://trade-consumer-cache-reader:8094";
    private int dbThreads = 10;
    private int cacheThreads = 20;
    private int intervalMs = 100;
    private boolean enabled = true;
    private boolean dbEnabled = true;
    private boolean cacheEnabled = true;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getDbReaderUrl() {
        return dbReaderUrl;
    }

    public void setDbReaderUrl(String dbReaderUrl) {
        this.dbReaderUrl = dbReaderUrl;
    }

    public String getCacheReaderUrl() {
        return cacheReaderUrl;
    }

    public void setCacheReaderUrl(String cacheReaderUrl) {
        this.cacheReaderUrl = cacheReaderUrl;
    }

    public int getDbThreads() {
        return dbThreads;
    }

    public void setDbThreads(int dbThreads) {
        this.dbThreads = dbThreads;
    }

    public int getCacheThreads() {
        return cacheThreads;
    }

    public void setCacheThreads(int cacheThreads) {
        this.cacheThreads = cacheThreads;
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDbEnabled() {
        return dbEnabled;
    }

    public void setDbEnabled(boolean dbEnabled) {
        this.dbEnabled = dbEnabled;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
}
