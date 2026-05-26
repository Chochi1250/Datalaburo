package com.DataLaburo.web.embedding;

public record EmbeddingGenerationResult(
        String provider,
        String model,
        int dimensions,
        float[] vector
) {
    public EmbeddingGenerationResult {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Embedding provider is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding model is required");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive");
        }
        if (vector == null) {
            throw new IllegalArgumentException("Embedding vector is required");
        }
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("Embedding vector length must match dimensions");
        }
        float[] copy = vector.clone();
        for (float value : copy) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding vector must not contain NaN or infinite values");
            }
        }
        vector = copy;
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
