package com.algoverse.analytics.repository;

import com.algoverse.analytics.domain.ProblemStat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemStatRepository extends JpaRepository<ProblemStat, UUID> {

    Optional<ProblemStat> findByProblemSlug(String problemSlug);

    List<ProblemStat> findByDifficulty(String difficulty);

    List<ProblemStat> findAllByOrderByTotalAttemptsDesc(Pageable pageable);

    @Query("SELECT SUM(ps.totalAttempts) FROM ProblemStat ps")
    Optional<Long> sumTotalAttempts();

    @Query("SELECT SUM(ps.totalAccepted) FROM ProblemStat ps")
    Optional<Long> sumTotalAccepted();

    /**
     * Find the hardest problems (lowest acceptance rate, minimum 10 attempts).
     */
    @Query("""
            SELECT ps FROM ProblemStat ps
            WHERE ps.totalAttempts >= 10
            ORDER BY (CAST(ps.totalAccepted AS double) / ps.totalAttempts) ASC
            """)
    List<ProblemStat> findHardestProblems(Pageable pageable);

    /**
     * Find problems with the best acceptance rate (minimum 10 attempts).
     */
    @Query("""
            SELECT ps FROM ProblemStat ps
            WHERE ps.totalAttempts >= 10
            ORDER BY (CAST(ps.totalAccepted AS double) / ps.totalAttempts) DESC
            """)
    List<ProblemStat> findBestAcceptanceRateProblems(Pageable pageable);

    /**
     * Upsert problem stats.
     * Atomically increments attempts; if accepted, also increments accepted count.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO problem_stats (id, problem_slug, difficulty, total_attempts, total_accepted, updated_at)
            VALUES (gen_random_uuid(), :slug, :difficulty, 1, :accepted, NOW())
            ON CONFLICT (problem_slug)
            DO UPDATE SET
                total_attempts = problem_stats.total_attempts + 1,
                total_accepted = problem_stats.total_accepted + :accepted,
                updated_at = NOW()
            """)
    void upsertSubmission(
            @Param("slug") String problemSlug,
            @Param("difficulty") String difficulty,
            @Param("accepted") int accepted
    );
}
