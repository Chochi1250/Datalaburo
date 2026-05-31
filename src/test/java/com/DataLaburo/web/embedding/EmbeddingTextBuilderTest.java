package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingTextBuilderTest {
    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void jobTextIncludesCoreTaggedFields() {
        Job job = new Job();
        job.setTitle("Backend Java Developer");
        job.setCompany("DataLab");
        job.setLocation("Buenos Aires");
        job.setDescription("Build APIs with Spring Boot.");
        job.setRequirementsText("Java\nPostgreSQL");

        String text = builder.buildForJob(job);

        assertTrue(text.contains("Title:\nBackend Java Developer"));
        assertTrue(text.contains("Company:\nDataLab"));
        assertTrue(text.contains("Location:\nBuenos Aires"));
        assertTrue(text.contains("Description:\nBuild APIs with Spring Boot."));
        assertTrue(text.contains("Requirements:\nJava\nPostgreSQL"));
    }

    @Test
    void visibleTextIsUsedOnlyWhenDescriptionIsBlank() {
        Job withDescription = new Job();
        withDescription.setDescription("Canonical description.");
        withDescription.setVisibleText("Noisy visible text.");

        String descriptionText = builder.buildForJob(withDescription);

        assertTrue(descriptionText.contains("Canonical description."));
        assertFalse(descriptionText.contains("Noisy visible text."));

        Job withoutDescription = new Job();
        withoutDescription.setVisibleText("Fallback visible text.");

        String fallbackText = builder.buildForJob(withoutDescription);

        assertTrue(fallbackText.contains("Description:\nFallback visible text."));
    }

    @Test
    void candidateProfileTextDoesNotIncludeName() {
        CandidateProfile profile = new CandidateProfile();
        profile.setName("Ada Lovelace");
        profile.setCvText("Java developer with PostgreSQL experience.");

        String text = builder.buildForCandidateProfile(profile);

        assertTrue(text.contains("CV:\nJava developer with PostgreSQL experience."));
        assertFalse(text.contains("Ada Lovelace"));
    }

    @Test
    void candidateProfileTextDoesNotIncludeProjectEvidence() {
        CandidateProfile profile = new CandidateProfile();
        profile.setCvText("Java developer with PostgreSQL experience.");

        CandidateProfileProject project = new CandidateProfileProject();
        project.setCandidateProfile(profile);
        project.setTitle("GraphQL portfolio API");
        project.setSkillsText("GraphQL, Docker");

        String text = builder.buildForCandidateProfile(profile);

        assertTrue(text.contains("CV:\nJava developer with PostgreSQL experience."));
        assertFalse(text.contains("GraphQL portfolio API"));
        assertFalse(text.contains("GraphQL"));
        assertFalse(text.contains("Docker"));
    }

    @Test
    void nullInputsReturnEmptyText() {
        assertTrue(builder.buildForJob(null).isEmpty());
        assertTrue(builder.buildForCandidateProfile(null).isEmpty());
    }
}
