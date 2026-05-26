package com.DataLaburo.web.embedding;

import java.util.List;

public interface EmbeddingVectorSearchRepository {
    boolean hasReadyProfileEmbedding(Long profileId, String embeddingModel, int embeddingDimensions);

    List<EmbeddingVectorSearchResult> searchReadyJobsForProfile(
            Long profileId,
            String embeddingModel,
            int embeddingDimensions,
            int limit,
            boolean semanticMeaning
    );
}
