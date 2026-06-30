package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingRepository;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProfileServiceTest {
    private final CandidateProfileRepository candidateProfileRepository = mock(CandidateProfileRepository.class);
    private final DocumentEmbeddingRepository documentEmbeddingRepository = mock(DocumentEmbeddingRepository.class);
    private final EmbeddingPreparationService embeddingPreparationService = mock(EmbeddingPreparationService.class);
    private final CandidateProfileService service = new CandidateProfileService(
            candidateProfileRepository,
            documentEmbeddingRepository,
            embeddingPreparationService
    );

    @Test
    void createsProfileWithVisibleMetadata() {
        CandidateProfileForm form = profileForm();
        form.setName("Backend profile");
        form.setCvText("Java backend CV");
        form.setHeadline("Backend Java junior");
        form.setSummary("Builds APIs with Spring Boot.");
        form.setDeclaredSkillsText(" Java, Spring Boot, PostgreSQL ");
        form.setLinkedinUrl(" https://www.linkedin.com/in/example ");
        form.setGithubUrl(" https://github.com/example ");
        form.setPortfolioUrl(" https://example.dev ");
        form.setAvatarPreset(" Java ");
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateProfile profile = service.create(form);

        assertEquals("Backend profile", profile.getName());
        assertEquals("Java backend CV", profile.getCvText());
        assertEquals("Backend Java junior", profile.getHeadline());
        assertEquals("Builds APIs with Spring Boot.", profile.getSummary());
        assertEquals("Java, Spring Boot, PostgreSQL", profile.getDeclaredSkillsText());
        assertEquals("https://www.linkedin.com/in/example", profile.getLinkedinUrl());
        assertEquals("https://github.com/example", profile.getGithubUrl());
        assertEquals("https://example.dev", profile.getPortfolioUrl());
        assertEquals("java", profile.getAvatarPreset());
        assertEquals("BACKEND", profile.getTargetRole());
        assertEquals("JUNIOR", profile.getTargetSeniority());
        assertEquals("FOCUSED", profile.getSearchMode());
        verify(embeddingPreparationService, never()).prepareCandidateProfile(any(CandidateProfile.class));
    }

    @Test
    void updatesCompleteProfileWithoutLosingEditableFields() {
        CandidateProfile profile = profile(7L, "Existing CV");
        profile.setAvatarPreset("java");
        CandidateProfileForm form = profileForm();
        form.setName(" Updated profile ");
        form.setCvText(" Existing CV ");
        form.setAvatarPreset(" kubernetes ");
        form.setHeadline("Cloud backend engineer");
        form.setSummary("Builds and operates backend services.");
        form.setDeclaredSkillsText(" Java, Kubernetes, PostgreSQL ");
        form.setLinkedinUrl(" https://www.linkedin.com/in/updated ");
        form.setGithubUrl(" https://github.com/updated ");
        form.setPortfolioUrl(" https://updated.dev ");
        form.setTargetRole("CLOUD");
        form.setTargetSeniority("MID");
        form.setSearchMode("EXPLORATORY");

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateProfile updated = service.updateFromForm(7L, form).orElseThrow();

        assertEquals("Updated profile", updated.getName());
        assertEquals("Existing CV", updated.getCvText());
        assertEquals("kubernetes", updated.getAvatarPreset());
        assertEquals("Cloud backend engineer", updated.getHeadline());
        assertEquals("Builds and operates backend services.", updated.getSummary());
        assertEquals("Java, Kubernetes, PostgreSQL", updated.getDeclaredSkillsText());
        assertEquals("https://www.linkedin.com/in/updated", updated.getLinkedinUrl());
        assertEquals("https://github.com/updated", updated.getGithubUrl());
        assertEquals("https://updated.dev", updated.getPortfolioUrl());
        assertEquals("CLOUD", updated.getTargetRole());
        assertEquals("MID", updated.getTargetSeniority());
        assertEquals("EXPLORATORY", updated.getSearchMode());
        verify(embeddingPreparationService, never()).prepareCandidateProfile(any(CandidateProfile.class));
    }

    @Test
    void keepsAvatarPresetNullForLegacyProfileFallback() {
        CandidateProfileForm form = profileForm();
        form.setName("Legacy profile");
        form.setCvText("Legacy CV");
        form.setAvatarPreset(null);
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateProfile profile = service.create(form);

        assertNull(profile.getAvatarPreset());
    }

    @Test
    void updatesVisibleMetadataWithoutPreparingEmbedding() {
        CandidateProfile profile = profile(7L, "Existing CV");
        CandidateProfileForm form = profileForm();
        form.setHeadline("Data analyst");
        form.setSummary("SQL and BI profile.");
        form.setDeclaredSkillsText(" SQL, Power BI, Excel ");
        form.setLinkedinUrl(" ");
        form.setGithubUrl(" https://github.com/data ");
        form.setPortfolioUrl("");
        form.setTargetRole("DATA");
        form.setTargetSeniority("MID");
        form.setSearchMode("BALANCED");

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateProfile updated = service.updateVisibleMetadata(7L, form).orElseThrow();

        assertEquals("Data analyst", updated.getHeadline());
        assertEquals("SQL and BI profile.", updated.getSummary());
        assertEquals("SQL, Power BI, Excel", updated.getDeclaredSkillsText());
        assertNull(updated.getLinkedinUrl());
        assertEquals("https://github.com/data", updated.getGithubUrl());
        assertNull(updated.getPortfolioUrl());
        assertEquals("DATA", updated.getTargetRole());
        assertEquals("MID", updated.getTargetSeniority());
        assertEquals("BALANCED", updated.getSearchMode());
        verify(candidateProfileRepository).save(profile);
        verify(embeddingPreparationService, never()).prepareCandidateProfile(any(CandidateProfile.class));
    }

    @Test
    void declaredSkillTagsAreTrimmedAndDeduplicatedForDisplay() {
        CandidateProfile profile = profile(7L, "Existing CV");
        profile.setDeclaredSkillsText(" Java, Spring Boot, , Java, SQL ");

        assertEquals(List.of("Java", "Spring Boot", "SQL"), profile.getDeclaredSkillTags());
    }

    @Test
    void changingCvTextSavesProfileAndPreparesEmbedding() {
        CandidateProfile profile = profile(7L, "Old CV");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        EmbeddingPreparationService.PreparationResult preparationResult =
                new EmbeddingPreparationService.PreparationResult(
                        EmbeddingPreparationService.PreparationAction.UPDATED,
                        embedding,
                        "source-hash",
                        null
                );

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingPreparationService.prepareCandidateProfile(profile)).thenReturn(preparationResult);

        CandidateProfileService.CvTextUpdateResult result = service.updateCvText(7L, " New CV ").orElseThrow();

        assertEquals(CandidateProfileService.CvTextUpdateAction.UPDATED, result.action());
        assertEquals("New CV", result.profile().getCvText());
        assertSame(preparationResult, result.preparationResult());
        verify(candidateProfileRepository).save(profile);
        verify(embeddingPreparationService).prepareCandidateProfile(profile);
    }

    @Test
    void unchangedCvTextDoesNotPrepareEmbedding() {
        CandidateProfile profile = profile(7L, "Same CV");
        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        CandidateProfileService.CvTextUpdateResult result = service.updateCvText(7L, " Same CV ").orElseThrow();

        assertEquals(CandidateProfileService.CvTextUpdateAction.UNCHANGED, result.action());
        assertEquals("Same CV", result.profile().getCvText());
        verify(candidateProfileRepository, never()).save(any(CandidateProfile.class));
        verify(embeddingPreparationService, never()).prepareCandidateProfile(any(CandidateProfile.class));
    }

    @Test
    void blankCvTextIsRejectedWithoutTouchingEmbedding() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateCvText(7L, "   ")
        );

        assertEquals("Pega el CV del perfil como texto.", error.getMessage());
        verify(candidateProfileRepository, never()).findById(any());
        verify(embeddingPreparationService, never()).prepareCandidateProfile(any(CandidateProfile.class));
    }

    @Test
    void findsCurrentProfileEmbeddingStatus() {
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.READY);
        when(documentEmbeddingRepository
                .findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndEmbeddingDimensionsAndNormalizerVersion(
                        eq(DocumentEmbeddingOwnerType.PROFILE),
                        eq(7L),
                        eq(DocumentEmbeddingSectionType.FULL_TEXT),
                        eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                        eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                        eq(EmbeddingTextNormalizer.VERSION)
                )).thenReturn(Optional.of(embedding));

        Optional<DocumentEmbedding> result = service.findProfileEmbedding(7L);

        assertTrue(result.isPresent());
        assertEquals(DocumentEmbeddingStatus.READY, result.orElseThrow().getStatus());
    }

    @Test
    void preparesCurrentProfileEmbeddingByProfileId() {
        CandidateProfile profile = profile(7L, "Java backend CV");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        EmbeddingPreparationService.PreparationResult preparationResult =
                new EmbeddingPreparationService.PreparationResult(
                        EmbeddingPreparationService.PreparationAction.CREATED,
                        embedding,
                        "source-hash",
                        null
                );

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(embeddingPreparationService.prepareCandidateProfile(profile)).thenReturn(preparationResult);

        Optional<EmbeddingPreparationService.PreparationResult> result = service.prepareProfileEmbedding(7L);

        assertTrue(result.isPresent());
        assertSame(preparationResult, result.orElseThrow());
        verify(embeddingPreparationService).prepareCandidateProfile(profile);
    }

    private static CandidateProfile profile(Long id, String cvText) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName("Profile");
        profile.setCvText(cvText);
        return profile;
    }

    private static CandidateProfileForm profileForm() {
        CandidateProfileForm form = new CandidateProfileForm();
        form.setTargetRole("BACKEND");
        form.setTargetSeniority("JUNIOR");
        form.setSearchMode("FOCUSED");
        return form;
    }

    private static DocumentEmbedding profileEmbedding(Long profileId, DocumentEmbeddingStatus status) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(100L);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.PROFILE);
        embedding.setOwnerId(profileId);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash("source-hash");
        embedding.setStatus(status);
        return embedding;
    }
}
