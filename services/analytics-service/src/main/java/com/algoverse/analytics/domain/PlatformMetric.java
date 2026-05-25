package com.algoverse.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily aggregated platform metrics stored in PostgreSQL.
 * Each row represents one metric key for one date (e.g., daily_signups on 2024-01-15).
 */
@Entity
@Table(
        name = "platform_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_platform_metrics_date_key",
                columnNames = {"metric_date", "metric_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PlatformMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "metric_key", nullable = false, length = 100)
    private String metricKey;

    @Column(name = "metric_value", nullable = false)
    @Builder.Default
    private Long metricValue = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
