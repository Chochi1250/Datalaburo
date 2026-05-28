package com.DataLaburo.web.analysis;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.service.RuleBasedEnrichmentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleDetectionServiceTest {
    @Test
    void databaseRoleWinsOverAzureDevOpsMention() {
        Job job = job("Desarrollador de base de datos");
        String text = "Experiencia de 4 anos en desarrollo Postgre SQL. SQL Server. Oracle Reports. Azure DevOps.";

        assertEquals("DATABASE", VectorFirstCompatibilityService.detectedRole(job, text, null));
    }

    @Test
    void detectsIamRole() {
        Job job = job("IAM Engineer");
        String text = "Identity and Access Management operations. OAuth OIDC SAML. SailPoint and Okta.";

        assertEquals("IAM", VectorFirstCompatibilityService.detectedRole(job, text, null));
    }

    @Test
    void detectsDotnetFullstackRole() {
        Job job = job("Analista Programador");
        String text = "Experiencia en .NET C# ASP.NET MVC. JavaScript avanzado. React deseable.";

        assertEquals("DOTNET_FULLSTACK", VectorFirstCompatibilityService.detectedRole(job, text, null));
    }

    @Test
    void detectsSeniorFromYears() {
        Job job = job("Application Support");
        String text = "A minimum of 5 years of experience in IT and application support.";

        assertEquals("SENIOR", VectorFirstCompatibilityService.detectedSeniority(job, text, null));
    }

    @Test
    void backendTraineeProjectsProfileRoleIsBackendEvenWithTestingLibraries() {
        String text = "Backend trainee developer. Academic and personal projects building REST APIs with Java, "
                + "Spring Boot, Maven, JUnit, Mockito, PostgreSQL, MySQL, Docker and Git. Built CRUD services.";
        RuleBasedEnrichmentService.EnrichedDocument enriched = enriched(
                RuleBasedEnrichmentService.Category.BACKEND,
                RuleBasedEnrichmentService.Category.QA
        );

        assertEquals("BACKEND", VectorFirstCompatibilityService.profileRole(enriched, text));
    }

    @Test
    void minorTestingMentionDoesNotOverrideBackendProfileRole() {
        String text = "Backend developer with Java, Spring Boot, REST APIs, PostgreSQL and automated testing experience.";
        RuleBasedEnrichmentService.EnrichedDocument enriched = enriched(
                RuleBasedEnrichmentService.Category.BACKEND,
                RuleBasedEnrichmentService.Category.QA
        );

        assertEquals("BACKEND", VectorFirstCompatibilityService.profileRole(enriched, text));
    }

    @Test
    void realQaProfileRoleRemainsQa() {
        String text = "QA automation tester focused on quality assurance, manual testing, test automation, Selenium and regression suites.";
        RuleBasedEnrichmentService.EnrichedDocument enriched = enriched(RuleBasedEnrichmentService.Category.QA);

        assertEquals("QA", VectorFirstCompatibilityService.profileRole(enriched, text));
    }

    private static Job job(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setSourceUrl("https://example.test/jobs");
        return job;
    }

    private static RuleBasedEnrichmentService.EnrichedDocument enriched(RuleBasedEnrichmentService.Category... categories) {
        return new RuleBasedEnrichmentService.EnrichedDocument(
                null,
                Set.of(),
                Set.of(categories),
                Set.of(),
                null,
                null,
                false,
                null,
                Map.of(),
                Map.of()
        );
    }
}
