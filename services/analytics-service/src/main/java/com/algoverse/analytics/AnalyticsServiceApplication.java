package com.algoverse.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AlgoVerse Analytics Service
 * <p>
 * Consumes Kafka events from submission, user, XP and badge topics,
 * aggregates metrics into PostgreSQL, stores raw events in MongoDB,
 * and exposes a REST API for dashboards and growth analytics.
 */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
