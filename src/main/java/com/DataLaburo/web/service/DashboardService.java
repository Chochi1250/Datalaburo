package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.DashboardStatsDto;
import com.DataLaburo.web.repository.JobOfferRepository;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.repository.JobSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DashboardService {
    private final JobRepository jobRepository;
    private final JobSnapshotRepository jobSnapshotRepository;
    private final JobOfferRepository jobOfferRepository;

    public DashboardService(
            JobRepository jobRepository,
            JobSnapshotRepository jobSnapshotRepository,
            JobOfferRepository jobOfferRepository
    ) {
        this.jobRepository = jobRepository;
        this.jobSnapshotRepository = jobSnapshotRepository;
        this.jobOfferRepository = jobOfferRepository;
    }

    public DashboardStatsDto getStats() {
        long total = jobRepository.count();
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        long last7Days = jobRepository.countCreatedSince(since);
        long withDescription = jobRepository.countWithDescription();
        long withRequirements = jobRepository.countWithRequirementsText();

        long snapshots = jobSnapshotRepository.count();
        long offers = jobOfferRepository.count();

        int pctDesc = total == 0 ? 0 : (int) Math.round(100.0 * ((double) withDescription / (double) total));
        int pctReq = total == 0 ? 0 : (int) Math.round(100.0 * ((double) withRequirements / (double) total));

        return new DashboardStatsDto(
                total,
                last7Days,
                withDescription,
                withRequirements,
                snapshots,
                offers,
                pctDesc,
                pctReq
        );
    }
}

