package com.algoverse.collaboration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollabEventProducer {

    private static final String ROOM_CREATED = "algoverse.collab.room.created";
    private static final String ROOM_ENDED   = "algoverse.collab.room.ended";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendRoomCreated(UUID roomId, UUID hostId, String roomType) {
        send(ROOM_CREATED, roomId.toString(), Map.of(
                "roomId", roomId.toString(),
                "hostId", hostId.toString(),
                "roomType", roomType,
                "occurredAt", Instant.now().toString()
        ));
    }

    public void sendRoomEnded(UUID roomId, UUID hostId, String roomType) {
        send(ROOM_ENDED, roomId.toString(), Map.of(
                "roomId", roomId.toString(),
                "hostId", hostId.toString(),
                "roomType", roomType,
                "occurredAt", Instant.now().toString()
        ));
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Kafka send failed [{}]: {}", topic, ex.getMessage());
                });
    }
}
