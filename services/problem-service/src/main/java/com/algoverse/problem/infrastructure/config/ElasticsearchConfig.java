package com.algoverse.problem.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration.
 *
 * Spring Boot auto-configures ElasticsearchClient from spring.elasticsearch.uris.
 * Repository scanning is handled by @EnableElasticsearchRepositories in ProblemServiceApplication.
 */
@Configuration
public class ElasticsearchConfig {
}
