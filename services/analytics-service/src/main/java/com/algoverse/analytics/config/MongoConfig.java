package com.algoverse.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.algoverse.analytics.repository")
public class MongoConfig {
    // Spring Boot auto-configuration handles connection via application.yml
    // This class enables auditing and ensures proper repository scanning
}
