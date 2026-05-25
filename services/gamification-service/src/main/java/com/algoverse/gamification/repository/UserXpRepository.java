package com.algoverse.gamification.repository;

import com.algoverse.gamification.domain.UserXp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserXpRepository extends JpaRepository<UserXp, UUID> {

    /**
     * Returns paginated users ordered by total XP descending (fallback for leaderboard).
     */
    Page<UserXp> findAllByOrderByTotalXpDesc(Pageable pageable);

    /**
     * Resets weekly_xp for all users. Called by a scheduled job every Monday.
     */
    @Modifying
    @Query("UPDATE UserXp u SET u.weeklyXp = 0")
    void resetAllWeeklyXp();

    /**
     * Resets monthly_xp for all users. Called by a scheduled job on the 1st of each month.
     */
    @Modifying
    @Query("UPDATE UserXp u SET u.monthlyXp = 0")
    void resetAllMonthlyXp();
}
