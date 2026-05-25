package com.algoverse.analytics.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Raw event log stored in MongoDB.
 * Captures every Kafka event as-is for forensic querying and user-level analytics.
 */
@Document(collection = "raw_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RawEvent {

    @Id
    private String id;

    /** One of: SUBMISSION_GRADED, USER_REGISTERED, XP_AWARDED, BADGE_EARNED */
    @Indexed
    private String eventType;

    @Indexed
    private String userId;

    private Map<String, Object> payload;

    @Indexed
    private Instant occurredAt;
}
