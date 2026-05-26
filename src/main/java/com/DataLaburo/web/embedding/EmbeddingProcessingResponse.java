package com.DataLaburo.web.embedding;

public record EmbeddingProcessingResponse(
        String embeddingModel,
        int embeddingDimensions,
        int scanned,
        int ready,
        int skipped,
        int failed
) {
}
