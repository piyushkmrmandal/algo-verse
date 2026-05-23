package com.algoverse.execution.domain.repository;

import com.algoverse.execution.domain.model.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * Paginated listing of all submissions for a given user, ordered by
     * submitted_at DESC (caller passes Pageable with sort).
     */
    Page<Submission> findByUserId(UUID userId, Pageable pageable);

    /**
     * Paginated listing filtered by both user and problem — used for the
     * "my submissions on this problem" view.
     */
    Page<Submission> findByUserIdAndProblemId(UUID userId, UUID problemId, Pageable pageable);

    /**
     * Counts submissions created after {@code after} for quota enforcement.
     */
    long countByUserIdAndSubmittedAtAfter(UUID userId, Instant after);
}
