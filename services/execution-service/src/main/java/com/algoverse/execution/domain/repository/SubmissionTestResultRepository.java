package com.algoverse.execution.domain.repository;

import com.algoverse.execution.domain.model.SubmissionTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, UUID> {

    List<SubmissionTestResult> findBySubmissionId(UUID submissionId);

    void deleteBySubmissionId(UUID submissionId);
}
