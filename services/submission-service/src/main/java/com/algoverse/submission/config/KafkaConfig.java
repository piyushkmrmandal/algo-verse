package com.algoverse.submission.config;

import com.algoverse.submission.dto.GradedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka topic and producer configuration for the submission service.
 */
@Configuration
public class KafkaConfig {

    @Value("${submission.topics.graded:algoverse.submission.graded}")
    private String gradedTopic;

    /**
     * Auto-creates the graded topic on startup if it does not exist.
     * In production, topics are provisioned via Terraform; this acts as a fallback.
     */
    @Bean
    public NewTopic gradedSubmissionTopic() {
        return TopicBuilder.name(gradedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, GradedEvent> gradedEventKafkaTemplate(
            ProducerFactory<String, GradedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
