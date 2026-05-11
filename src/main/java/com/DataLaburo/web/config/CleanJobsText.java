package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.JobTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
public class CleanJobsText {
    private static final Logger log = LoggerFactory.getLogger(CleanJobsText.class);

    @Bean
    @Order(20)
    CommandLineRunner cleanJobsTextRunner(JobRepository jobRepository) {
        return new Runner(jobRepository);
    }

    static class Runner implements CommandLineRunner {
        private final JobRepository jobRepository;

        Runner(JobRepository jobRepository) {
            this.jobRepository = jobRepository;
        }

        @Override
        @Transactional
        public void run(String... args) {
            List<Job> jobs = jobRepository.findAll();
            long updated = 0;

            for (Job job : jobs) {
                boolean changed = false;

                changed |= cleanLocation(job);

                String cleanedDescription = cleanIfDirty(job.getDescription());
                String cleanedVisible = cleanIfDirty(job.getVisibleText());

                // Prefer the longer/denser source when description looks cut (restore full text when possible).
                String baseForSplit = preferLongerNonBlank(cleanedDescription, cleanedVisible, 400);
                String refinedFullText = JobTextCleaner.refineDescription(baseForSplit, job.getTitle(), job.getCompany(), job.getLocation());
                if (!equalsNullable(refinedFullText, job.getDescription()) && !isBlank(refinedFullText)) {
                    job.setDescription(refinedFullText);
                    changed = true;
                }

                if (!equalsNullable(cleanedVisible, job.getVisibleText()) && !isBlank(cleanedVisible)) {
                    job.setVisibleText(cleanedVisible);
                    changed = true;
                }

                if (isBlank(job.getDescription())) {
                    String base = job.getVisibleText();
                    String baseClean = JobTextCleaner.clean(base);
                    String baseRefined = JobTextCleaner.refineDescription(baseClean, job.getTitle(), job.getCompany(), job.getLocation());
                    if (!isBlank(baseRefined)) {
                        job.setDescription(baseRefined);
                        changed = true;
                    }
                }

                if (changed) {
                    jobRepository.save(job);
                    updated++;
                }
            }

            log.info("Clean text in JOBS done. Scanned={}, updated={}", jobs.size(), updated);
        }

        private static boolean cleanLocation(Job job) {
            String current = job.getLocation();
            String rawCandidate = firstNonBlank(current, job.getLocationRaw());
            if (isBlank(rawCandidate)) {
                return false;
            }
            if (!isBlank(current) && !JobTextCleaner.looksDirtyLocation(current)) {
                return false;
            }
            String cleaned = JobTextCleaner.cleanLocation(rawCandidate);
            if (isBlank(cleaned)) {
                cleaned = "Ubicación no especificada";
            }
            if (equalsNullable(cleaned, current)) {
                return false;
            }

            // Preserve the previous noisy value in locationRaw (if not already present).
            if (isBlank(job.getLocationRaw()) && !isBlank(current) && !cleaned.equals(current)) {
                job.setLocationRaw(current);
            }
            job.setLocation(cleaned);
            return true;
        }

        private static String cleanIfDirty(String value) {
            if (isBlank(value)) {
                return null;
            }
            if (!JobTextCleaner.looksDirty(value)) {
                return value;
            }
            return JobTextCleaner.clean(value);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String firstNonBlank(String a, String b) {
            if (!isBlank(a)) {
                return a;
            }
            return !isBlank(b) ? b : null;
        }

        private static String preferLongerNonBlank(String a, String b, int minDelta) {
            if (isBlank(a)) {
                return b;
            }
            if (isBlank(b)) {
                return a;
            }
            if (b.length() >= a.length() + Math.max(0, minDelta)) {
                return b;
            }
            return a;
        }

        private static boolean equalsNullable(String a, String b) {
            if (a == null) {
                return b == null;
            }
            return a.equals(b);
        }
    }
}
