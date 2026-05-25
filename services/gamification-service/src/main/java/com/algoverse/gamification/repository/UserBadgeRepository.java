package com.algoverse.gamification.repository;

import com.algoverse.gamification.domain.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    /**
     * Returns all badges earned by a user, most recently earned first.
     */
    @Query("SELECT ub FROM UserBadge ub JOIN FETCH ub.badge WHERE ub.userId = :userId ORDER BY ub.earnedAt DESC")
    List<UserBadge> findByUserIdOrderByEarnedAtDesc(UUID userId);

    /**
     * Checks whether a user already holds a specific badge (deduplication guard).
     */
    boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);
}
