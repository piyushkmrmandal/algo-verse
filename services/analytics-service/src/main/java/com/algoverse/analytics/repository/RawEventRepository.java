package com.algoverse.analytics.repository;

import com.algoverse.analytics.domain.RawEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RawEventRepository extends MongoRepository<RawEvent, String> {

    List<RawEvent> findByUserIdOrderByOccurredAtDesc(String userId);

    List<RawEvent> findByUserIdAndEventTypeOrderByOccurredAtDesc(String userId, String eventType);

    List<RawEvent> findByUserIdAndEventTypeAndOccurredAtBetween(
            String userId, String eventType, Instant from, Instant to);

    List<RawEvent> findByUserIdAndOccurredAtBetween(String userId, Instant from, Instant to);

    long countByUserIdAndEventType(String userId, String eventType);

    @Query("{ 'userId': ?0, 'eventType': 'SUBMISSION_GRADED', 'payload.status': 'ACCEPTED' }")
    List<RawEvent> findAcceptedSubmissionsByUserId(String userId);

    @Query("{ 'userId': ?0, 'eventType': 'SUBMISSION_GRADED', 'payload.status': 'ACCEPTED', 'occurredAt': { $gte: ?1, $lte: ?2 } }")
    List<RawEvent> findAcceptedSubmissionsByUserIdAndDateRange(
            String userId, Instant from, Instant to);
}
