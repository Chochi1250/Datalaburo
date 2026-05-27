package com.DataLaburo.web.analysis;

import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.Test;

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

    private static Job job(String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setSourceUrl("https://example.test/jobs");
        return job;
    }
}
