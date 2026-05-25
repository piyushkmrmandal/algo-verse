package com.algoverse.gamification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily activity streak tracking per user.
 *
 * <p>A streak increments when the user solves at least 1 problem per calendar day (UTC).
 * If a user misses a day, the streak resets to 1 (the current day still counts).
 * {@code lastActivityDate} is used to determine whether today's solve extends,
 * starts fresh, or is a duplicate within the same day.
 */
@Entity
@Table(name = "streaks")
@Getter
@Setter
@NoArgsConstructor
public class UserStreak {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "freeze_count", nullable = false)
    private int freezeCount = 2;

    @Column(name = "next_reset_at")
    private Instant nextResetAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserStreak(UUID userId) {
        this.userId = userId;
        this.currentStreak = 0;
        this.longestStreak = 0;
    }
}
