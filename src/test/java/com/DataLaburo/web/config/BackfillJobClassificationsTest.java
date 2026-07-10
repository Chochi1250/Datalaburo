package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.JobClassificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackfillJobClassificationsTest {
    private static final Instant NOW = Instant.parse("2026-07-03T12:00:00Z");
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobClassificationService classificationService = new JobClassificationService(
            Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"))
    );

    @Test
    void backfillOnlyCompletesMissingClassificationFields() {
        Job missing = new Job();
        missing.setTitle("Software Engineer");

        Job alreadyClassified = new Job();
        alreadyClassified.setTitle("Backend Engineer");
        alreadyClassified.setRoleFamily("BACKEND");
        alreadyClassified.setRoleSeniority("SENIOR");
        alreadyClassified.setWorkModality("REMOTE");
        alreadyClassified.setEmploymentType("FULLTIME");
        alreadyClassified.setClassificationVersion(JobClassificationService.CLASSIFICATION_VERSION);
        alreadyClassified.setClassifiedAt(NOW.minusSeconds(60));

        when(jobRepository.findAll()).thenReturn(List.of(missing, alreadyClassified));

        new BackfillJobClassifications.Runner(jobRepository, classificationService).run();

        assertEquals("SOFTWARE_ENGINEERING_GENERAL", missing.getRoleFamily());
        assertEquals(JobClassificationService.CLASSIFICATION_VERSION, missing.getClassificationVersion());
        assertEquals(NOW, missing.getClassifiedAt());
        verify(jobRepository).save(missing);
        verify(jobRepository, never()).save(alreadyClassified);
    }

    @Test
    void backfillRevisitsPartiallyClassifiedJobsWithoutOverwritingExistingStructuredValues() {
        Instant existingClassifiedAt = NOW.minusSeconds(120);
        Job partial = new Job();
        partial.setTitle("Senior Backend Engineer");
        partial.setDescription("Full-time remote role building APIs.");
        partial.setLocation("Argentina Remote");
        partial.setRoleFamily("DATA");
        partial.setClassificationVersion(JobClassificationService.CLASSIFICATION_VERSION);
        partial.setClassifiedAt(existingClassifiedAt);

        when(jobRepository.findAll()).thenReturn(List.of(partial));

        new BackfillJobClassifications.Runner(jobRepository, classificationService).run();

        assertEquals("DATA", partial.getRoleFamily());
        assertEquals("SENIOR", partial.getRoleSeniority());
        assertEquals("REMOTE", partial.getWorkModality());
        assertEquals("FULLTIME", partial.getEmploymentType());
        assertEquals(JobClassificationService.CLASSIFICATION_VERSION, partial.getClassificationVersion());
        assertEquals(existingClassifiedAt, partial.getClassifiedAt());
        verify(jobRepository).save(partial);
    }
}
