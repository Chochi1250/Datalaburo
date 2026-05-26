package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class EmbeddingVectorSearchService {
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final int EMBEDDING_DIMENSIONS = DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS;
    private static final boolean SEMANTIC_MEANING = false;
    private static final String FAKE_RESULT_MESSAGE = "Internal pgvector infrastructure search only. "
            + "fake-deterministic-1024 does not represent real professional compatibility.";

    private final EmbeddingVectorSearchRepository vectorSearchRepository;

    public EmbeddingVectorSearchService(EmbeddingVectorSearchRepository vectorSearchRepository) {
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public EmbeddingVectorSearchResponse searchJobsForProfile(
            Long profileId,
            Integer limit,
            String embeddingModel
    ) {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile id must be positive");
        }

        String normalizedModel = normalizeEmbeddingModel(embeddingModel);
        int normalizedLimit = normalizeLimit(limit);

        boolean hasProfile = vectorSearchRepository.hasReadyProfileEmbedding(
                profileId,
                normalizedModel,
                EMBEDDING_DIMENSIONS
        );
        if (!hasProfile) {
            return response(
                    profileId,
                    normalizedModel,
                    SEMANTIC_MEANING,
                    "No READY profile embedding was found for the requested model.",
                    List.of()
            );
        }

        List<EmbeddingVectorSearchResult> results = vectorSearchRepository.searchReadyJobsForProfile(
                profileId,
                normalizedModel,
                EMBEDDING_DIMENSIONS,
                normalizedLimit,
                SEMANTIC_MEANING
        );
        if (results.isEmpty()) {
            return response(
                    profileId,
                    normalizedModel,
                    SEMANTIC_MEANING,
                    messageFor(normalizedModel, "No READY job embeddings were found for the requested model."),
                    results
            );
        }

        return response(
                profileId,
                normalizedModel,
                SEMANTIC_MEANING,
                messageFor(normalizedModel, "Vector ranking returned successfully."),
                results
        );
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    static String normalizeEmbeddingModel(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL;
        }
        return embeddingModel.trim();
    }

    private static String messageFor(String embeddingModel, String baseMessage) {
        if (DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL.equals(embeddingModel)) {
            return baseMessage + " " + FAKE_RESULT_MESSAGE;
        }
        return baseMessage;
    }

    private static EmbeddingVectorSearchResponse response(
            Long profileId,
            String embeddingModel,
            boolean semanticMeaning,
            String message,
            List<EmbeddingVectorSearchResult> results
    ) {
        return new EmbeddingVectorSearchResponse(
                profileId,
                embeddingModel,
                EMBEDDING_DIMENSIONS,
                semanticMeaning,
                message,
                results
        );
    }
}
