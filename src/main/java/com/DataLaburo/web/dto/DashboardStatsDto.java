package com.DataLaburo.web.dto;

public record DashboardStatsDto(
        long totalJobs,
        long jobsLast7Days,
        long jobsWithDescription,
        long jobsWithRequirementsText,
        long jobSnapshotsTotal,
        long jobOffersTotal,
        int percentWithDescription,
        int percentWithRequirementsText
) {
}

