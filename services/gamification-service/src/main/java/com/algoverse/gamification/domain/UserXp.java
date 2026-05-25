package com.algoverse.gamification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate XP totals per user. Single source of truth for level and rank.
 *
 * <p>The {@code level} field is derived: {@code level = floor(sqrt(totalXp / 100)) + 1}.
 * It is recomputed and persisted on each XP award to avoid expensive recalculation
 * on reads.
 */
@Entity
@Table(name = "user_xp")
@Getter
@Setter
@NoArgsConstructor
public class UserXp {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_xp", nullable = false)
    private int totalXp = 0;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "weekly_xp", nullable = false)
    private int weeklyXp = 0;

    @Column(name = "monthly_xp", nullable = false)
    private int monthlyXp = 0;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "easy_solves", nullable = false)
    private int easySolves = 0;

    @Column(name = "medium_solves", nullable = false)
    private int mediumSolves = 0;

    @Column(name = "hard_solves", nullable = false)
    private int hardSolves = 0;

    @Column(name = "total_solves", nullable = false)
    private int totalSolves = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserXp(UUID userId) {
        this.userId = userId;
        this.totalXp = 0;
        this.level = 1;
    }

    /**
     * Compute level from XP: level = floor(sqrt(totalXp / 100)) + 1
     */
    public static int computeLevel(int totalXp) {
        return (int) Math.floor(Math.sqrt((double) totalXp / 100.0)) + 1;
    }

    /**
     * XP required to reach the next level from current totalXp.
     */
    public static int xpToNextLevel(int totalXp) {
        int currentLevel = computeLevel(totalXp);
        // Next level threshold: ((nextLevel - 1)^2) * 100
        int nextLevelXp = (currentLevel) * (currentLevel) * 100;
        return Math.max(0, nextLevelXp - totalXp);
    }

    public void addXp(int amount) {
        this.totalXp += amount;
        this.weeklyXp += amount;
        this.monthlyXp += amount;
        this.level = computeLevel(this.totalXp);
    }

    public void incrementSolves(String difficulty) {
        this.totalSolves++;
        switch (difficulty.toUpperCase()) {
            case "EASY"   -> this.easySolves++;
            case "MEDIUM" -> this.mediumSolves++;
            case "HARD"   -> this.hardSolves++;
        }
    }
}
