package com.algoverse.problem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * AlgoVerse Problem Service
 *
 * Multi-store service:
 *   - PostgreSQL (JPA)           → domain/repository/**
 *   - MongoDB                    → infrastructure/mongo/**
 *   - Elasticsearch              → infrastructure/elasticsearch/**
 *   - Redis                      → cache layer (Spring Cache abstraction)
 *
 * Repository scan packages are explicitly scoped to prevent Spring Data
 * from applying the wrong store adapter to repositories.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableKafka
@EnableJpaRepositories(basePackages = "com.algoverse.problem.domain.repository")
@EnableMongoRepositories(basePackages = "com.algoverse.problem.infrastructure.mongo")
@EnableElasticsearchRepositories(basePackages = "com.algoverse.problem.infrastructure.elasticsearch")
public class ProblemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProblemServiceApplication.class, args);
    }
}
