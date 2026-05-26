package com.DataLaburo.web.embedding;

public record EmbeddingStatusResponse(
        long total,
        long pending,
        long ready,
        long failed
) {
}
