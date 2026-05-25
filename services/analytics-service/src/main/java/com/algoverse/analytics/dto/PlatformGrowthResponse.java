package com.algoverse.analytics.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Platform growth time series response for GET /api/v1/analytics/platform/growth.
 */
public record PlatformGrowthResponse(
        String period,
        long totalSignups,
        long totalSubmissions,
        List<GrowthDataPoint> dataPoints
) implements Serializable {}
