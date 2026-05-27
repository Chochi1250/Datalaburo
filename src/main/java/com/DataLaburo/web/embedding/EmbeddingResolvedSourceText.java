package com.DataLaburo.web.embedding;

public record EmbeddingResolvedSourceText(
        String normalizedText,
        String sourceTextHash
) {
}
