package com.DataLaburo.web.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static CandidateProfile profile(Long id, String cvText) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName("Profile");
        profile.setCvText(cvText);
        return profile;
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
