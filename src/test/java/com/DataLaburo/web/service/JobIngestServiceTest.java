package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.ScrapeCurrentRequestDto;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.model.JobSnapshot;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.repository.JobSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobIngestServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-03T12:00:00Z");
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSnapshotRepository jobSnapshotRepository = mock(JobSnapshotRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
    private final JobIngestService service = new JobIngestService(
            jobRepository,
            jobSnapshotRepository,
            new JobPublicationDateService(clock),
            new JobClassificationService(clock)
    );

    @Test
    void classifiesNewJobsWithVersionAndFixedTimestamp() {
        when(jobRepository.findTopBySourceAndExternalJobIdOrderByIdDesc("linkedin", "123")).thenReturn(Optional.empty());
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobSnapshotRepository.save(any(JobSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobIngestService.IngestResult result = service.ingest(payload(
                "https://www.linkedin.com/jobs/view/123",
                "Senior Backend Engineer",
                "Argentina Remote",
                "Full-time remote role building APIs."
        ));

        Job job = result.job();
        assertFalse(result.deduplicated());
        assertEquals("BACKEND", job.getRoleFamily());
        assertEquals("SENIOR", job.getRoleSeniority());
        assertEquals("REMOTE", job.getWorkModality());
        assertEquals("FULLTIME", job.getEmploymentType());
        assertEquals(JobClassificationService.CLASSIFICATION_VERSION, job.getClassificationVersion());
        assertEquals(NOW, job.getClassifiedAt());
    }

    @Test
    void duplicateIngestCompletesOnlyMissingClassificationFields() {
        Job existing = new Job();
        existing.setId(7L);
        existing.setSource("linkedin");
        existing.setExternalJobId("456");
        existing.setSourceUrl("https://www.linkedin.com/jobs/view/456");
        existing.setTitle("Senior Backend Engineer");
        existing.setRoleFamily("DATA");

        when(jobRepository.findTopBySourceAndExternalJobIdOrderByIdDesc("linkedin", "456")).thenReturn(Optional.of(existing));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobIngestService.IngestResult result = service.ingest(payload(
                "https://www.linkedin.com/jobs/view/456",
                "Senior Backend Engineer",
                "Argentina Remote",
                "Full-time remote role building APIs."
        ));

        assertTrue(result.deduplicated());
        assertEquals("DATA", existing.getRoleFamily());
        assertEquals("SENIOR", existing.getRoleSeniority());
        assertEquals("REMOTE", existing.getWorkModality());
        assertEquals("FULLTIME", existing.getEmploymentType());
        assertEquals(JobClassificationService.CLASSIFICATION_VERSION, existing.getClassificationVersion());
        assertEquals(NOW, existing.getClassifiedAt());
        verify(jobRepository).save(existing);
    }

    private static ScrapeCurrentRequestDto payload(String url, String title, String location, String description) {
        ScrapeCurrentRequestDto payload = new ScrapeCurrentRequestDto();
        payload.setUrl(url);
        payload.setTitle(title);
        payload.setCompany("Example");
        payload.setLocation(location);
        payload.setJobDescription(description);
        payload.setPostedAtText("hace 1 dia");
        return payload;
    }
}
