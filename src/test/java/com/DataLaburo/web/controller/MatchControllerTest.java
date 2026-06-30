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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void jobsUsesFifteenItemsPerPageAndSelectsFirstVisibleJob() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        String viewName = controller.jobs(null, null, null, null, null, null, "1", null, model);

        assertEquals("jobs", viewName);
        List<Job> jobs = jobsFrom(model);
        assertEquals(15, jobs.size());
        assertEquals(1L, jobs.get(0).getId());
        assertEquals(15L, jobs.get(14).getId());
        assertEquals(22, model.getAttribute("totalJobs"));
        assertEquals(15, model.getAttribute("pageSize"));
        assertEquals(1, model.getAttribute("currentPage"));
        assertEquals(2, model.getAttribute("totalPages"));
        assertEquals(1, model.getAttribute("pageStart"));
        assertEquals(15, model.getAttribute("pageEnd"));
        assertEquals(1L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsSecondPageShowsRemainingItems() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs(null, null, null, null, null, null, "2", null, model);

        List<Job> jobs = jobsFrom(model);
        assertEquals(7, jobs.size());
        assertEquals(16L, jobs.get(0).getId());
        assertEquals(22L, jobs.get(6).getId());
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(16, model.getAttribute("pageStart"));
        assertEquals(22, model.getAttribute("pageEnd"));
        assertEquals(16L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsOutOfRangePageUsesLastAvailablePage() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs(null, null, null, null, null, null, "99", null, model);

        List<Job> jobs = jobsFrom(model);
        assertEquals(7, jobs.size());
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(16L, jobs.get(0).getId());
        assertEquals(16L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsFiltersBeforePaginatingAndKeepsQueryInModel() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(jobsWithLocations(22, 16));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs("Buenos", null, null, null, null, null, "2", null, model);

        List<Job> jobs = jobsFrom(model);
        assertEquals(1, jobs.size());
        assertEquals(16L, jobs.get(0).getId());
        assertEquals(16, model.getAttribute("totalJobs"));
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(16, model.getAttribute("pageStart"));
        assertEquals(16, model.getAttribute("pageEnd"));
        assertEquals("Buenos", model.getAttribute("q"));
    }

    @Test
    void jobsUnknownSelectedJobFallsBackToFirstVisibleOnCurrentPage() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs(null, null, null, null, null, null, "2", 999L, model);

        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(16L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsSelectedJobIsKeptWhenItBelongsToCurrentPage() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs(null, null, null, null, null, null, "2", 18L, model);

        assertEquals(18L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsMovesToPageContainingSelectedJobWhenNeeded() {
        when(jobRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(numberedJobs(22));
        ConcurrentModel model = new ConcurrentModel();

        controller.jobs(null, null, null, null, null, null, "1", 18L, model);

        List<Job> jobs = jobsFrom(model);
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(16L, jobs.get(0).getId());
        assertEquals(18L, model.getAttribute("selectedJobId"));
    }

    @Test
    void jobsTemplateKeepsPublicOfferActionAndNoRankingMode() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/jobs.html"));

        assertTrue(template.contains("Ver oferta"));
        assertFalse(template.contains("Ver JSON"));
        assertFalse(template.contains("rankingMode"));
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

    private static List<Job> numberedJobs(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(MatchControllerTest::job)
                .toList();
    }

    private static List<Job> jobsWithLocations(int count, int buenosAiresCount) {
        List<Job> jobs = numberedJobs(count);
        for (int i = 0; i < jobs.size(); i++) {
            jobs.get(i).setLocation(i < buenosAiresCount ? "Buenos Aires" : "Cordoba");
        }
        return jobs;
    }

    @SuppressWarnings("unchecked")
    private static List<Job> jobsFrom(ConcurrentModel model) {
        return (List<Job>) model.getAttribute("jobs");
    }

    private static String longCvText() {
        return """
                Backend Java developer with Spring Boot, REST APIs, PostgreSQL, Docker and Git experience.
                Built production services, tested endpoints, documented technical decisions and collaborated with product teams.
                Experience includes support, monitoring, SQL troubleshooting, backend integrations and deployment workflows.
                """;
    }
}
