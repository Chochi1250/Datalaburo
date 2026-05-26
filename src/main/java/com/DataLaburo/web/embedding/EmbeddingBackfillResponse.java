package com.DataLaburo.web.embedding;

public record EmbeddingBackfillResponse(
        int scanned,
        int created,
        int updated,
        int unchanged,
        int skippedBlank,
        int failed
) {
    public static EmbeddingBackfillResponse empty() {
        return new EmbeddingBackfillResponse(0, 0, 0, 0, 0, 0);
    }
}
