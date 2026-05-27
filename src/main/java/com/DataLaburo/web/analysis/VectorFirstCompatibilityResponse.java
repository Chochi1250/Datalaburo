package com.DataLaburo.web.analysis;

import java.util.List;

public record VectorFirstCompatibilityResponse(
        Long profileId,
        String embeddingModel,
        int embeddingDimensions,
        Retrieval retrieval,
        List<VectorFirstCompatibilityResult> results
) {
    public record Retrieval(
            int limit,
            String strategy
    ) {
    }
}
