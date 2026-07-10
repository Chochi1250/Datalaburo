package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.model.JobSnapshot;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.repository.JobSnapshotRepository;
import com.DataLaburo.web.service.JobPublicationDateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Configuration
public class BackfillJobPublicationDates {
    private static final Logger log = LoggerFactory.getLogger(BackfillJobPublicationDates.class);

    @Bean
    @Order(20)
    @ConditionalOnProperty(name = "datalaburo.backfill-publication-dates.enabled", havingValue = "true")
    CommandLineRunner backfillJobPublicationDatesRunner(
            JobRepository jobRepository,
            JobSnapshotRepository jobSnapshotRepository,
            JobPublicationDateService publicationDateService
    ) {
        return new Runner(jobRepository, jobSnapshotRepository, publicationDateService);
    }

    static class Runner implements CommandLineRunner {
        private final JobRepository jobRepository;
        private final JobSnapshotRepository jobSnapshotRepository;
        private final JobPublicationDateService publicationDateService;

        Runner(
                JobRepository jobRepository,
                JobSnapshotRepository jobSnapshotRepository,
                JobPublicationDateService publicationDateService
        ) {
            this.jobRepository = jobRepository;
            this.jobSnapshotRepository = jobSnapshotRepository;
            this.publicationDateService = publicationDateService;
        }

        @Override
        @Transactional
        public void run(String... args) {
            List<Job> jobs = jobRepository.findAll();
            long updated = 0;
            long skippedUnparseable = 0;
            long skippedAlreadyEstimated = 0;
            long skippedMissingObservedAt = 0;

            for (Job job : jobs) {
                if (job.getPublishedAtEstimated() != null) {
                    skippedAlreadyEstimated++;
                    continue;
                }

                String postedAtText = safeTrim(job.getPostedAtText());
                if (postedAtText == null || publicationDateService.parseRelativeDuration(postedAtText).isEmpty()) {
                    skippedUnparseable++;
                    continue;
                }

                Instant observedAt = observedAtFor(job, postedAtText);
                if (observedAt == null) {
                    skippedMissingObservedAt++;
                    continue;
                }

                Optional<Instant> estimated = publicationDateService.estimatePublishedAt(postedAtText, observedAt);
                if (estimated.isEmpty()) {
                    skippedUnparseable++;
                    continue;
                }

                job.setPostedAtObservedAt(observedAt);
                job.setPublishedAtEstimated(estimated.get());
                jobRepository.save(job);
                updated++;
            }

            log.info(
                    "Backfill publication dates done. Analyzed={}, updated={}, skippedUnparseable={}, skippedAlreadyEstimated={}, skippedMissingObservedAt={}",
                    jobs.size(),
                    updated,
                    skippedUnparseable,
                    skippedAlreadyEstimated,
                    skippedMissingObservedAt
            );
        }

        private Instant observedAtFor(Job job, String postedAtText) {
            if (job.getId() != null) {
                Optional<JobSnapshot> snapshot = jobSnapshotRepository
                        .findTopByJobIdAndPostedAtTextOrderByCapturedAtDescIdDesc(job.getId(), postedAtText);
                if (snapshot.isPresent()) {
                    return snapshot.get().getCapturedAt();
                }
            }
            return job.getCreatedAt();
        }

        private static String safeTrim(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
