package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.JobClassification;
import com.DataLaburo.web.service.JobClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
public class BackfillJobClassifications {
    private static final Logger log = LoggerFactory.getLogger(BackfillJobClassifications.class);

    @Bean
    @Order(30)
    @ConditionalOnProperty(name = "datalaburo.backfill-job-classifications.enabled", havingValue = "true")
    CommandLineRunner backfillJobClassificationsRunner(
            JobRepository jobRepository,
            JobClassificationService classificationService
    ) {
        return new Runner(jobRepository, classificationService);
    }

    static class Runner implements CommandLineRunner {
        private final JobRepository jobRepository;
        private final JobClassificationService classificationService;

        Runner(JobRepository jobRepository, JobClassificationService classificationService) {
            this.jobRepository = jobRepository;
            this.classificationService = classificationService;
        }

        @Override
        @Transactional
        public void run(String... args) {
            List<Job> jobs = jobRepository.findAll();
            long updated = 0;
            long skippedAlreadyClassified = 0;

            for (Job job : jobs) {
                if (isFullyClassified(job)) {
                    skippedAlreadyClassified++;
                    continue;
                }
                if (applyMissingClassification(job)) {
                    jobRepository.save(job);
                    updated++;
                }
            }

            log.info(
                    "Backfill job classifications done. Analyzed={}, updated={}, skippedAlreadyClassified={}",
                    jobs.size(),
                    updated,
                    skippedAlreadyClassified
            );
        }

        private boolean applyMissingClassification(Job job) {
            JobClassification classification = classificationService.classify(job);
            boolean changed = false;

            if (isBlank(job.getRoleFamily())) {
                job.setRoleFamily(classification.roleFamily().name());
                changed = true;
            }
            if (classification.roleSpecialty() != null && isBlank(job.getRoleSpecialty())) {
                job.setRoleSpecialty(classification.roleSpecialty());
                changed = true;
            }
            if (classification.roleSeniority() != null && isBlank(job.getRoleSeniority())) {
                job.setRoleSeniority(classification.roleSeniority());
                changed = true;
            }
            if (classification.workModality() != null && isBlank(job.getWorkModality())) {
                job.setWorkModality(classification.workModality());
                changed = true;
            }
            if (classification.employmentType() != null && isBlank(job.getEmploymentType())) {
                job.setEmploymentType(classification.employmentType());
                changed = true;
            }
            if (isBlank(job.getClassificationVersion())) {
                job.setClassificationVersion(JobClassificationService.CLASSIFICATION_VERSION);
                changed = true;
            }
            if (job.getClassifiedAt() == null) {
                job.setClassifiedAt(classificationService.classifiedAtNow());
                changed = true;
            }

            return changed;
        }

        private static boolean isFullyClassified(Job job) {
            return !isBlank(job.getRoleFamily())
                    && !isBlank(job.getRoleSeniority())
                    && !isBlank(job.getWorkModality())
                    && !isBlank(job.getEmploymentType())
                    && !isBlank(job.getClassificationVersion())
                    && job.getClassifiedAt() != null;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
