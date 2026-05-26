package com.DataLaburo.web.embedding;

import java.util.List;

public record EmbeddingVectorSearchResponse(
        Long profileId,
        String embeddingModel,
        int embeddingDimensions,
        boolean semanticMeaning,
        String message,
        List<EmbeddingVectorSearchResult> results
) {
}
