package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConditionalOnProperty(
        prefix = "datalaburo.embeddings",
        name = "generator",
        havingValue = "fake",
        matchIfMissing = true
)
public class DeterministicFakeEmbeddingGenerator implements EmbeddingGenerator {
    private static final String PROVIDER = "local-fake";
    private static final int VALUES_PER_SHA256_DIGEST = 8;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL;
    }

    @Override
    public int dimensions() {
        return DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS;
    }

    @Override
    public EmbeddingGenerationResult generate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Embedding input is required");
        }

        float[] vector = new float[dimensions()];
        int vectorIndex = 0;
        int blockIndex = 0;
        while (vectorIndex < vector.length) {
            byte[] digest = sha256(model() + ":" + input + ":" + blockIndex);
            ByteBuffer buffer = ByteBuffer.wrap(digest);
            for (int i = 0; i < VALUES_PER_SHA256_DIGEST && vectorIndex < vector.length; i++) {
                long unsigned = Integer.toUnsignedLong(buffer.getInt());
                vector[vectorIndex++] = (float) ((unsigned / 4_294_967_295.0d) * 2.0d - 1.0d);
            }
            blockIndex++;
        }
        normalize(vector);

        return new EmbeddingGenerationResult(provider(), model(), dimensions(), vector);
    }

    private static byte[] sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void normalize(float[] vector) {
        double squaredNorm = 0.0d;
        for (float value : vector) {
            squaredNorm += (double) value * value;
        }
        if (squaredNorm == 0.0d) {
            return;
        }
        double norm = Math.sqrt(squaredNorm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
