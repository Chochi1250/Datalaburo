package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class PostgresDocumentEmbeddingVectorWriter implements DocumentEmbeddingVectorWriter {
    private static final String UPDATE_READY_SQL = """
            update document_embeddings
               set embedding = cast(? as vector),
                   status = ?,
                   error_message = null,
                   last_embedded_at = now(),
                   updated_at = now()
             where id = ?
               and status = ?
               and embedding_model = ?
               and embedding_dimensions = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresDocumentEmbeddingVectorWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean writeReady(DocumentEmbedding documentEmbedding, EmbeddingGenerationResult generationResult) {
        if (documentEmbedding == null || documentEmbedding.getId() == null) {
            throw new IllegalArgumentException("Document embedding id is required");
        }
        if (generationResult == null) {
            throw new IllegalArgumentException("Embedding generation result is required");
        }

        int updated = jdbcTemplate.update(
                UPDATE_READY_SQL,
                toPgVectorLiteral(generationResult.vector()),
                DocumentEmbeddingStatus.READY.name(),
                documentEmbedding.getId(),
                DocumentEmbeddingStatus.PENDING.name(),
                generationResult.model(),
                generationResult.dimensions()
        );
        return updated == 1;
    }

    private static String toPgVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding vector must not contain NaN or infinite values");
            }
            builder.append(Float.toString(value));
        }
        builder.append(']');
        return builder.toString();
    }
}
