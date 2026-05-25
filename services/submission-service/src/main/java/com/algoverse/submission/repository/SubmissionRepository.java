package com.algoverse.submission.repository;

import com.algoverse.submission.domain.Submission;
import com.algoverse.submission.domain.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /** Paginated history for a user. */
    Page<Submission> findByUserIdOrderBySubmittedAtDesc(UUID userId, Pageable pageable);

    /** All attempts by a user on a specific problem. */
    List<Submission> findByUserIdAndProblemSlugOrderBySubmittedAtDesc(UUID userId, String problemSlug);

    /** Check if a user has at least one ACCEPTED submission for a problem (used for isFirstSolve). */
    @Query("SELECT COUNT(s) > 0 FROM Submission s " +
           "WHERE s.userId = :userId AND s.problemSlug = :problemSlug AND s.status = :status")
    boolean existsByUserIdAndProblemSlugAndStatus(
            @Param("userId") UUID userId,
            @Param("problemSlug") String problemSlug,
            @Param("status") SubmissionStatus status
    );
}
