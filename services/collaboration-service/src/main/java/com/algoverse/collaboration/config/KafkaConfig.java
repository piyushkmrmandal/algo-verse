package com.algoverse.collaboration.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic collabRoomCreatedTopic() {
        return TopicBuilder.name("algoverse.collab.room.created")
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic collabRoomEndedTopic() {
        return TopicBuilder.name("algoverse.collab.room.ended")
                .partitions(3).replicas(1).build();
    }
}
