package com.algoverse.analytics.repository;

import com.algoverse.analytics.domain.PlatformMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformMetricRepository extends JpaRepository<PlatformMetric, UUID> {

    Optional<PlatformMetric> findByMetricDateAndMetricKey(LocalDate metricDate, String metricKey);

    List<PlatformMetric> findByMetricKey(String metricKey);

    List<PlatformMetric> findByMetricDateBetween(LocalDate startDate, LocalDate endDate);

    List<PlatformMetric> findByMetricKeyAndMetricDateBetween(
            String metricKey, LocalDate startDate, LocalDate endDate);

    @Query("SELECT SUM(pm.metricValue) FROM PlatformMetric pm WHERE pm.metricKey = :key")
    Optional<Long> sumByMetricKey(@Param("key") String key);

    @Query("SELECT SUM(pm.metricValue) FROM PlatformMetric pm WHERE pm.metricKey = :key AND pm.metricDate = :date")
    Optional<Long> sumByMetricKeyAndDate(@Param("key") String key, @Param("date") LocalDate date);

    /**
     * Upsert a metric using PostgreSQL ON CONFLICT.
     * Atomically increments the metric_value by the given delta.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO platform_metrics (id, metric_date, metric_key, metric_value, created_at)
            VALUES (gen_random_uuid(), :metricDate, :metricKey, :delta, NOW())
            ON CONFLICT (metric_date, metric_key)
            DO UPDATE SET metric_value = platform_metrics.metric_value + :delta
            """)
    void upsertIncrement(
            @Param("metricDate") LocalDate metricDate,
            @Param("metricKey") String metricKey,
            @Param("delta") long delta
    );
}
