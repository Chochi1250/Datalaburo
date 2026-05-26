package com.DataLaburo.web.embedding;

public record EmbeddingPrepareResponse(
        String action,
        String ownerType,
        Long ownerId,
        String sectionType,
        String embeddingModel,
        Integer embeddingDimensions,
        String normalizerVersion,
        String status,
        String sourceTextHash,
        String reason
) {
    public static EmbeddingPrepareResponse from(EmbeddingPreparationService.PreparationResult result) {
        DocumentEmbedding embedding = result == null ? null : result.documentEmbedding();
        return new EmbeddingPrepareResponse(
                result == null || result.action() == null ? null : result.action().name(),
                embedding == null || embedding.getOwnerType() == null ? null : embedding.getOwnerType().name(),
                embedding == null ? null : embedding.getOwnerId(),
                embedding == null || embedding.getSectionType() == null ? null : embedding.getSectionType().name(),
                embedding == null ? DocumentEmbedding.DEFAULT_EMBEDDING_MODEL : embedding.getEmbeddingModel(),
                embedding == null ? DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS : embedding.getEmbeddingDimensions(),
                embedding == null ? EmbeddingTextNormalizer.VERSION : embedding.getNormalizerVersion(),
                embedding == null || embedding.getStatus() == null ? null : embedding.getStatus().name(),
                result == null ? null : result.sourceTextHash(),
                result == null ? null : result.reason()
        );
    }
}
