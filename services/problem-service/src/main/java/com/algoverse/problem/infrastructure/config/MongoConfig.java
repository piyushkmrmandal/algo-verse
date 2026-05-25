package com.algoverse.problem.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * MongoDB configuration.
 *
 * Spring Boot auto-configures MongoClient from spring.data.mongodb.uri.
 * Repository scanning is handled by @EnableMongoRepositories in ProblemServiceApplication.
 */
@Configuration
public class MongoConfig {
}
