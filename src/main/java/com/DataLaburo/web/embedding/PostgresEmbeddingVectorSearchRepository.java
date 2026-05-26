package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class PostgresEmbeddingVectorSearchRepository implements EmbeddingVectorSearchRepository {
    private static final String PROFILE_EXISTS_SQL = """
            select exists (
                select 1
                  from document_embeddings
                 where owner_type = 'PROFILE'
                   and owner_id = ?
                   and section_type = 'FULL_TEXT'
                   and embedding_model = ?
                   and embedding_dimensions = ?
                   and status = 'READY'
                   and embedding is not null
            )
            """;

    private static final String VECTOR_SEARCH_SQL = """
            with profile_embedding as (
                select embedding
                  from document_embeddings
                 where owner_type = 'PROFILE'
                   and owner_id = ?
                   and section_type = 'FULL_TEXT'
                   and embedding_model = ?
                   and embedding_dimensions = ?
                   and status = 'READY'
                   and embedding is not null
                 limit 1
            )
            select job.owner_id as job_id,
                   job.id as job_embedding_id,
                   job.embedding_model,
                   job.embedding <=> profile_embedding.embedding as distance,
                   1 - (job.embedding <=> profile_embedding.embedding) as similarity
              from document_embeddings job
              cross join profile_embedding
             where job.owner_type = 'JOB'
               and job.section_type = 'FULL_TEXT'
               and job.embedding_model = ?
               and job.embedding_dimensions = ?
               and job.status = 'READY'
               and job.embedding is not null
             order by job.embedding <=> profile_embedding.embedding asc
             limit ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresEmbeddingVectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasReadyProfileEmbedding(Long profileId, String embeddingModel, int embeddingDimensions) {
        Boolean exists = jdbcTemplate.queryForObject(
                PROFILE_EXISTS_SQL,
                Boolean.class,
                profileId,
                embeddingModel,
                embeddingDimensions
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<EmbeddingVectorSearchResult> searchReadyJobsForProfile(
            Long profileId,
            String embeddingModel,
            int embeddingDimensions,
            int limit,
            boolean semanticMeaning
    ) {
        return jdbcTemplate.query(
                VECTOR_SEARCH_SQL,
                (rs, rowNum) -> new EmbeddingVectorSearchResult(
                        rs.getLong("job_id"),
                        rs.getLong("job_embedding_id"),
                        rs.getDouble("distance"),
                        rs.getDouble("similarity"),
                        rs.getString("embedding_model"),
                        semanticMeaning
                ),
                profileId,
                embeddingModel,
                embeddingDimensions,
                embeddingModel,
                embeddingDimensions,
                limit
        );
    }
}
