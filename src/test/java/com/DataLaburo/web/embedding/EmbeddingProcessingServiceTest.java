package com.DataLaburo.web.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingProcessingServiceTest {
    private final DocumentEmbeddingRepository repository = mock(DocumentEmbeddingRepository.class);
    private final EmbeddingGenerator generator = new DeterministicFakeEmbeddingGenerator();
    private final DocumentEmbeddingVectorWriter vectorWriter = mock(DocumentEmbeddingVectorWriter.class);
    private final EmbeddingProcessingService service = new EmbeddingProcessingService(
            repository,
            generator,
            vectorWriter
    );

    @Test
    void processPendingQueriesOnlyPendingFakeEmbeddings() {
        DocumentEmbedding pendingFake = embedding(
                1L,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL,
                DocumentEmbeddingStatus.PENDING
        );
        when(repository.findByStatusAndEmbeddingModelAndEmbeddingDimensionsOrderByUpdatedAtAscIdAsc(
                eq(DocumentEmbeddingStatus.PENDING),
                eq(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL),
                eq(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        )).thenReturn(List.of(pendingFake));
        when(vectorWriter.writeReady(eq(pendingFake), any(EmbeddingGenerationResult.class))).thenReturn(true);

        EmbeddingProcessingResponse response = service.processPending(100);

        assertEquals(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL, response.embeddingModel());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, response.embeddingDimensions());
        assertEquals(1, response.scanned());
        assertEquals(1, response.ready());
        assertEquals(0, response.skipped());
        assertEquals(0, response.failed());
        verify(vectorWriter).writeReady(eq(pendingFake), any(EmbeddingGenerationResult.class));
        verify(repository, never()).save(any(DocumentEmbedding.class));
    }

    @Test
    void processByIdDoesNotProcessBgeM3Embeddings() {
        DocumentEmbedding bgePending = embedding(
                2L,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbeddingStatus.PENDING
        );
        when(repository.findById(2L)).thenReturn(Optional.of(bgePending));

        EmbeddingProcessingResult result = service.processById(2L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.SKIPPED, result.action());
        assertEquals(DocumentEmbeddingStatus.PENDING, bgePending.getStatus());
        verify(vectorWriter, never()).writeReady(any(DocumentEmbedding.class), any(EmbeddingGenerationResult.class));
        verify(repository, never()).save(any(DocumentEmbedding.class));
    }

    @Test
    void processByIdDoesNotTouchReadyEmbeddings() {
        DocumentEmbedding readyFake = embedding(
                3L,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL,
                DocumentEmbeddingStatus.READY
        );
        when(repository.findById(3L)).thenReturn(Optional.of(readyFake));

        EmbeddingProcessingResult result = service.processById(3L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.SKIPPED, result.action());
        assertEquals(DocumentEmbeddingStatus.READY, readyFake.getStatus());
        verify(vectorWriter, never()).writeReady(any(DocumentEmbedding.class), any(EmbeddingGenerationResult.class));
        verify(repository, never()).save(any(DocumentEmbedding.class));
    }

    @Test
    void writerErrorMarksFailedWithoutMarkingReady() {
        DocumentEmbedding pendingFake = embedding(
                4L,
                DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL,
                DocumentEmbeddingStatus.PENDING
        );
        when(repository.findById(4L)).thenReturn(Optional.of(pendingFake));
        when(vectorWriter.writeReady(eq(pendingFake), any(EmbeddingGenerationResult.class)))
                .thenThrow(new IllegalStateException("pgvector unavailable"));

        EmbeddingProcessingResult result = service.processById(4L).orElseThrow();

        assertEquals(EmbeddingProcessingAction.FAILED, result.action());
        assertEquals(DocumentEmbeddingStatus.FAILED, pendingFake.getStatus());
        assertEquals("pgvector unavailable", pendingFake.getErrorMessage());
        assertNull(pendingFake.getLastEmbeddedAt());
        verify(repository).save(pendingFake);
    }

    private static DocumentEmbedding embedding(Long id, String model, DocumentEmbeddingStatus status) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(id);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.JOB);
        embedding.setOwnerId(42L);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(model);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash("source-hash-" + id);
        embedding.setStatus(status);
        return embedding;
    }
}
