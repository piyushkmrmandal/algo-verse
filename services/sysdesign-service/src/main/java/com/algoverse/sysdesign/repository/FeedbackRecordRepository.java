package com.algoverse.sysdesign.repository;

import com.algoverse.sysdesign.domain.FeedbackRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRecordRepository extends MongoRepository<FeedbackRecord, String> {

    Optional<FeedbackRecord> findTopByDiagramIdOrderByGeneratedAtDesc(String diagramId);

    List<FeedbackRecord> findByDiagramId(String diagramId);

    List<FeedbackRecord> findByUserId(String userId);
}
