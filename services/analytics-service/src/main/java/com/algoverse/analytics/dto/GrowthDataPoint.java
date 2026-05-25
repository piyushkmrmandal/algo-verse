package com.algoverse.analytics.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * A single data point in the platform growth time series.
 */
public record GrowthDataPoint(
        LocalDate date,
        long signups,
        long submissions
) implements Serializable {}
