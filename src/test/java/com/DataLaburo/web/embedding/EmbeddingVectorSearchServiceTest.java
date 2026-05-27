package com.DataLaburo.web.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EmbeddingVectorSearchServiceTest {
    private final EmbeddingVectorSearchRepository repository = mock(EmbeddingVectorSearchRepository.class);
    private final EmbeddingVectorSearchService service = new EmbeddingVectorSearchService(repository);

    @Test
    void validatesLimitWithDefaultAndMaximum() {
        assertEquals(EmbeddingVectorSearchService.DEFAULT_LIMIT, EmbeddingVectorSearchService.normalizeLimit(null));
        assertEquals(EmbeddingVectorSearchService.DEFAULT_LIMIT, EmbeddingVectorSearchService.normalizeLimit(0));
        assertEquals(5, EmbeddingVectorSearchService.normalizeLimit(5));
        assertEquals(EmbeddingVectorSearchService.MAX_LIMIT, EmbeddingVectorSearchService.normalizeLimit(999));
    }

    @Test
    void usesFakeModelByDefaultAndCapsLimitBeforeSearching() {
        Long profileId = 7L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(true);
        when(repository.searchReadyJobsForProfile(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                eq(EmbeddingVectorSearchService.MAX_LIMIT),
                eq(false)
        )).thenReturn(List.of(result(10L)));

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(profileId, 999, null);

        assertEquals(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL, response.embeddingModel());
        assertFalse(response.semanticMeaning());
        assertEquals(1, response.results().size());
    }

    @Test
    void passesExplicitEmbeddingModelToRepository() {
        Long profileId = 8L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(true);
        when(repository.searchReadyJobsForProfile(
                eq(profileId),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                eq(20),
                eq(true)
        )).thenReturn(List.of(result(11L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, true)));

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(
                profileId,
                20,
                " " + DocumentEmbedding.DEFAULT_EMBEDDING_MODEL + " "
        );

        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, response.embeddingModel());
        assertTrue(response.semanticMeaning());
        assertTrue(response.results().get(0).semanticMeaning());
        assertTrue(response.message().contains("Semantic vector ranking based on real BGE-M3 embeddings"));
        verify(repository).hasReadyProfileEmbedding(
                profileId,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS
        );
    }

    @Test
    void responseIncludesSemanticMeaningFalseForFakeDeterministicModel() {
        Long profileId = 9L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(true);
        when(repository.searchReadyJobsForProfile(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                eq(20),
                eq(false)
        )).thenReturn(List.of(result(12L)));

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(
                profileId,
                20,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL
        );

        assertFalse(response.semanticMeaning());
        assertFalse(response.results().get(0).semanticMeaning());
        assertTrue(response.message().contains("does not represent real professional compatibility"));
    }

    @Test
    void responseIncludesSemanticMeaningTrueForBgeM3Model() {
        Long profileId = 12L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(true);
        when(repository.searchReadyJobsForProfile(
                eq(profileId),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                eq(20),
                eq(true)
        )).thenReturn(List.of(result(13L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, true)));

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(
                profileId,
                20,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL
        );

        assertTrue(response.semanticMeaning());
        assertTrue(response.results().get(0).semanticMeaning());
        assertTrue(response.message().contains("Semantic vector ranking based on real BGE-M3 embeddings"));
    }

    @Test
    void missingProfileEmbeddingReturnsClearResponseWithoutSearchingJobs() {
        Long profileId = 10L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(false);

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(profileId, 20, null);

        assertTrue(response.results().isEmpty());
        assertTrue(response.message().contains("No READY profile embedding was found for the requested model."));
        assertTrue(response.message().contains("does not represent real professional compatibility"));
        verify(repository).hasReadyProfileEmbedding(
                profileId,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS
        );
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noReadyJobsReturnsEmptyListWithClearMessage() {
        Long profileId = 11L;
        when(repository.hasReadyProfileEmbedding(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS)
        )).thenReturn(true);
        when(repository.searchReadyJobsForProfile(
                eq(profileId),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                eq(20),
                eq(false)
        )).thenReturn(List.of());

        EmbeddingVectorSearchResponse response = service.searchJobsForProfile(profileId, 20, null);

        assertTrue(response.results().isEmpty());
        assertTrue(response.message().contains("No READY job embeddings were found"));
    }

    private static EmbeddingVectorSearchResult result(Long jobId) {
        return result(jobId, DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL, false);
    }

    private static EmbeddingVectorSearchResult result(Long jobId, String embeddingModel, boolean semanticMeaning) {
        return new EmbeddingVectorSearchResult(
                jobId,
                jobId + 1_000,
                0.25d,
                0.75d,
                embeddingModel,
                semanticMeaning
        );
    }
}
