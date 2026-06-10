package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CvMatchingForm;
import com.DataLaburo.web.dto.JobMatchRowView;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvMatchingService;
import com.DataLaburo.web.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchControllerTest {
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final CvMatchingService cvMatchingService = mock(CvMatchingService.class);
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final MatchController controller = new MatchController(
            jobRepository,
            dashboardService,
            cvMatchingService,
            candidateProfileService
    );

    @Test
    void postMatchingKeepsUsingLegacyCvMatchingService() throws Exception {
        String cvText = longCvText();
        CvMatchingService.CvMatchResult result = emptyResult();
        when(candidateProfileService.findAll()).thenReturn(List.of());
        when(cvMatchingService.matchAgainstAllJobs(cvText, 50)).thenReturn(result);

        CvMatchingForm form = new CvMatchingForm();
        form.setCvText(cvText);
        ConcurrentModel model = new ConcurrentModel();

        String viewName = controller.matchCv(form, model);

        assertEquals("matching", viewName);
        assertSame(result, model.getAttribute("result"));

        verify(cvMatchingService).matchAgainstAllJobs(cvText, 50);
    }

    @Test
    void postJobMatchKeepsUsingLegacyCvMatchingService() throws Exception {
        Job job = job(42L);
        CandidateProfile profile = profile(7L, longCvText());
        JobMatchRowView result = new JobMatchRowView(
                42L,
                "Backend Engineer",
                "Example",
                70,
                List.of(),
                List.of("Java"),
                List.of("Afinidad tecnica"),
                List.of(),
                List.of("Backend"),
                "Junior",
                70,
                20,
                10
        );

        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findAll()).thenReturn(List.of(profile));
        when(cvMatchingService.matchAgainstJob(profile.getCvText(), job)).thenReturn(result);

        CvMatchingForm form = new CvMatchingForm();
        form.setProfileId(7L);
        ConcurrentModel model = new ConcurrentModel();

        String viewName = controller.matchJob(42L, form, model);

        assertEquals("job-match", viewName);
        assertSame(result, model.getAttribute("result"));

        verify(cvMatchingService).matchAgainstJob(profile.getCvText(), job);
    }

    private static CvMatchingService.CvMatchResult emptyResult() {
        return new CvMatchingService.CvMatchResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                0,
                0
        );
    }

    private static CandidateProfile profile(Long id, String cvText) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName("Profile");
        profile.setCvText(cvText);
        return profile;
    }

    private static Job job(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setTitle("Backend Engineer");
        job.setCompany("Example");
        job.setSourceUrl("https://example.com/jobs/42");
        return job;
    }

    private static String longCvText() {
        return """
                Backend Java developer with Spring Boot, REST APIs, PostgreSQL, Docker and Git experience.
                Built production services, tested endpoints, documented technical decisions and collaborated with product teams.
                Experience includes support, monitoring, SQL troubleshooting, backend integrations and deployment workflows.
                """;
    }
}
