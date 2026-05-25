package com.algoverse.gamification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Badge catalogue entry (predefined by the platform).
 * Badge definitions are seeded via Flyway migration V2.
 */
@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url")
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private BadgeRarity rarity = BadgeRarity.COMMON;

    @Column(name = "xp_reward", nullable = false)
    private int xpReward = 0;

    @Column(name = "condition_type", nullable = false, length = 100)
    private String conditionType;

    @Column(name = "condition_value", columnDefinition = "JSONB", nullable = false)
    private String conditionValue;

    public enum BadgeRarity {
        COMMON, RARE, EPIC, LEGENDARY
    }
}
