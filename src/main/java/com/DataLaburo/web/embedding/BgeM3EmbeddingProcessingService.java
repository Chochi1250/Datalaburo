package com.DataLaburo.web.embedding;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class BgeM3EmbeddingProcessingService {
    private static final int DEFAULT_LIMIT = 1;
    private static final int MAX_LIMIT = 10;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final DocumentEmbeddingRepository documentEmbeddingRepository;
    private final EmbeddingSourceTextResolver sourceTextResolver;
    private final BgeM3EmbeddingGenerator embeddingGenerator;
    private final DocumentEmbeddingVectorWriter vectorWriter;

    public BgeM3EmbeddingProcessingService(
            DocumentEmbeddingRepository documentEmbeddingRepository,
            EmbeddingSourceTextResolver sourceTextResolver,
            BgeM3EmbeddingGenerator embeddingGenerator,
            DocumentEmbeddingVectorWriter vectorWriter
    ) {
        this.documentEmbeddingRepository = documentEmbeddingRepository;
        this.sourceTextResolver = sourceTextResolver;
        this.embeddingGenerator = embeddingGenerator;
        this.vectorWriter = vectorWriter;
    }

    @Transactional
    public EmbeddingProcessingResponse processPending(Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(
                Sort.Order.asc("updatedAt"),
                Sort.Order.asc("id")
        ));
        List<DocumentEmbedding> candidates =
                documentEmbeddingRepository.findByStatusAndEmbeddingModelAndEmbeddingDimensionsOrderByUpdatedAtAscIdAsc(
                        DocumentEmbeddingStatus.PENDING,
                        embeddingGenerator.model(),
                        embeddingGenerator.dimensions(),
                        page
                );

        Counter counter = new Counter();
        for (DocumentEmbedding candidate : candidates) {
            counter.apply(process(candidate));
        }
        return counter.toResponse(embeddingGenerator.model(), embeddingGenerator.dimensions());
    }

    @Transactional
    public Optional<EmbeddingProcessingResult> processById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return documentEmbeddingRepository.findById(id).map(this::process);
    }

    @Transactional
    public Optional<EmbeddingProcessingResult> resetFailedById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return documentEmbeddingRepository.findById(id).map(this::resetFailed);
    }

    private EmbeddingProcessingResult process(DocumentEmbedding documentEmbedding) {
        if (documentEmbedding.getStatus() != DocumentEmbeddingStatus.PENDING) {
            return skipped(documentEmbedding, "Only PENDING BAAI/bge-m3 embeddings are processed");
        }
        if (!Objects.equals(documentEmbedding.getEmbeddingModel(), embeddingGenerator.model())) {
            return skipped(documentEmbedding, "Only BAAI/bge-m3 embeddings are processed by the real worker");
        }
        if (!Objects.equals(documentEmbedding.getEmbeddingDimensions(), embeddingGenerator.dimensions())) {
            return fail(
                    documentEmbedding,
                    "Expected embedding dimensions " + embeddingGenerator.dimensions()
                            + " but found " + documentEmbedding.getEmbeddingDimensions()
            );
        }

        try {
            EmbeddingResolvedSourceText resolvedText = sourceTextResolver.resolve(documentEmbedding);
            validateSourceHash(documentEmbedding, resolvedText);

            EmbeddingGenerationResult generationResult = embeddingGenerator.generate(resolvedText.normalizedText());
            validateGenerationResult(generationResult);

            boolean written = vectorWriter.writeReady(documentEmbedding, generationResult);
            if (!written) {
                return fail(documentEmbedding, "No pending BAAI/bge-m3 embedding row was updated");
            }

            return new EmbeddingProcessingResult(
                    EmbeddingProcessingAction.READY,
                    documentEmbedding.getId(),
                    documentEmbedding.getEmbeddingModel(),
                    documentEmbedding.getEmbeddingDimensions(),
                    null
            );
        } catch (RuntimeException e) {
            return fail(documentEmbedding, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private EmbeddingProcessingResult resetFailed(DocumentEmbedding documentEmbedding) {
        if (!Objects.equals(documentEmbedding.getEmbeddingModel(), embeddingGenerator.model())) {
            return skipped(documentEmbedding, "Only BAAI/bge-m3 embeddings can be reset by this endpoint");
        }
        if (documentEmbedding.getStatus() != DocumentEmbeddingStatus.FAILED) {
            return skipped(documentEmbedding, "Only FAILED BAAI/bge-m3 embeddings can be reset");
        }
        documentEmbedding.setStatus(DocumentEmbeddingStatus.PENDING);
        documentEmbedding.setErrorMessage(null);
        documentEmbedding.setLastEmbeddedAt(null);
        DocumentEmbedding saved = documentEmbeddingRepository.save(documentEmbedding);
        return new EmbeddingProcessingResult(
                EmbeddingProcessingAction.SKIPPED,
                saved.getId(),
                saved.getEmbeddingModel(),
                saved.getEmbeddingDimensions(),
                "FAILED BAAI/bge-m3 embedding reset to PENDING; run process-bge-m3 again"
        );
    }

    private void validateSourceHash(DocumentEmbedding documentEmbedding, EmbeddingResolvedSourceText resolvedText) {
        if (!Objects.equals(documentEmbedding.getSourceTextHash(), resolvedText.sourceTextHash())) {
            throw new IllegalStateException(
                    "Source text hash mismatch; run metadata backfill before generating BAAI/bge-m3 embeddings"
            );
        }
    }

    private void validateGenerationResult(EmbeddingGenerationResult generationResult) {
        if (!Objects.equals(generationResult.model(), embeddingGenerator.model())) {
            throw new IllegalStateException("BGE-M3 generator returned unexpected model: " + generationResult.model());
        }
        if (generationResult.dimensions() != embeddingGenerator.dimensions()) {
            throw new IllegalStateException("BGE-M3 generator returned unexpected dimensions: " + generationResult.dimensions());
        }
        float[] vector = generationResult.vector();
        if (vector.length == 0) {
            throw new IllegalStateException("BGE-M3 generator returned an empty vector");
        }
        if (vector.length != embeddingGenerator.dimensions()) {
            throw new IllegalStateException("BGE-M3 generator returned vector length " + vector.length);
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("BGE-M3 generator returned NaN or infinite values");
            }
        }
    }

    private EmbeddingProcessingResult fail(DocumentEmbedding documentEmbedding, String errorMessage) {
        documentEmbedding.setStatus(DocumentEmbeddingStatus.FAILED);
        documentEmbedding.setErrorMessage(truncate(errorMessage));
        documentEmbedding.setLastEmbeddedAt(null);
        documentEmbeddingRepository.save(documentEmbedding);
        return new EmbeddingProcessingResult(
                EmbeddingProcessingAction.FAILED,
                documentEmbedding.getId(),
                documentEmbedding.getEmbeddingModel(),
                documentEmbedding.getEmbeddingDimensions(),
                documentEmbedding.getErrorMessage()
        );
    }

    private static EmbeddingProcessingResult skipped(DocumentEmbedding documentEmbedding, String reason) {
        return new EmbeddingProcessingResult(
                EmbeddingProcessingAction.SKIPPED,
                documentEmbedding.getId(),
                documentEmbedding.getEmbeddingModel(),
                documentEmbedding.getEmbeddingDimensions(),
                reason
        );
    }

    private static String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static final class Counter {
        private int scanned;
        private int ready;
        private int skipped;
        private int failed;

        private void apply(EmbeddingProcessingResult result) {
            scanned++;
            if (result == null || result.action() == null) {
                failed++;
                return;
            }
            switch (result.action()) {
                case READY -> ready++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        private EmbeddingProcessingResponse toResponse(String embeddingModel, int embeddingDimensions) {
            return new EmbeddingProcessingResponse(embeddingModel, embeddingDimensions, scanned, ready, skipped, failed);
        }
    }
}
