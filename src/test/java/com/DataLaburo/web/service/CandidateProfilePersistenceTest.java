package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CandidateProfilePersistenceTest {
    @Autowired
    private CandidateProfileService candidateProfileService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsCompleteProfileAndReopensEditedAvatarPreset() {
        CandidateProfileForm createForm = completeForm("java", "Initial headline");
        CandidateProfile created = candidateProfileService.create(createForm);
        Long profileId = created.getId();

        entityManager.flush();
        entityManager.clear();

        CandidateProfile reopened = candidateProfileRepository.findById(profileId).orElseThrow();
        assertEquals("java", reopened.getAvatarPreset());
        assertEquals("Initial headline", reopened.getHeadline());

        CandidateProfileForm editForm = completeForm("kubernetes", "Updated headline");
        editForm.setName("Updated profile");
        candidateProfileService.updateFromForm(profileId, editForm).orElseThrow();

        entityManager.flush();
        entityManager.clear();

        CandidateProfile edited = candidateProfileRepository.findById(profileId).orElseThrow();
        assertEquals("Updated profile", edited.getName());
        assertEquals("Complete CV", edited.getCvText());
        assertEquals("kubernetes", edited.getAvatarPreset());
        assertEquals("Updated headline", edited.getHeadline());
        assertEquals("Complete summary", edited.getSummary());
        assertEquals("Java, Spring Boot, PostgreSQL", edited.getDeclaredSkillsText());
        assertEquals("https://www.linkedin.com/in/example", edited.getLinkedinUrl());
        assertEquals("https://github.com/example", edited.getGithubUrl());
        assertEquals("https://example.dev", edited.getPortfolioUrl());
        assertEquals("BACKEND", edited.getTargetRole());
        assertEquals("JUNIOR", edited.getTargetSeniority());
        assertEquals("FOCUSED", edited.getSearchMode());
    }

    private static CandidateProfileForm completeForm(String avatarPreset, String headline) {
        CandidateProfileForm form = new CandidateProfileForm();
        form.setName("Complete profile");
        form.setCvText("Complete CV");
        form.setAvatarPreset(avatarPreset);
        form.setHeadline(headline);
        form.setSummary("Complete summary");
        form.setDeclaredSkillsText("Java, Spring Boot, PostgreSQL");
        form.setLinkedinUrl("https://www.linkedin.com/in/example");
        form.setGithubUrl("https://github.com/example");
        form.setPortfolioUrl("https://example.dev");
        form.setTargetRole("BACKEND");
        form.setTargetSeniority("JUNIOR");
        form.setSearchMode("FOCUSED");
        return form;
    }
}
