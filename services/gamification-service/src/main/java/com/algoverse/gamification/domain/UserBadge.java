package com.algoverse.gamification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Join entity recording which badges have been awarded to which users.
 * The UNIQUE constraint on (user_id, badge_id) prevents duplicate awards.
 */
@Entity
@Table(
    name = "user_badges",
    uniqueConstraints = @UniqueConstraint(
        name = "user_badges_unique",
        columnNames = {"user_id", "badge_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_user_badge_badge"))
    private Badge badge;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    public UserBadge(UUID userId, Badge badge) {
        this.userId = userId;
        this.badge = badge;
        this.earnedAt = Instant.now();
    }
}
