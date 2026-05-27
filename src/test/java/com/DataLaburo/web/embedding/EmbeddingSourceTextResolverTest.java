package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import com.DataLaburo.web.repository.JobRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingSourceTextResolverTest {
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final CandidateProfileRepository candidateProfileRepository = mock(CandidateProfileRepository.class);
    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();
    private final EmbeddingTextNormalizer textNormalizer = new EmbeddingTextNormalizer();
    private final SourceTextHasher sourceTextHasher = new SourceTextHasher();
    private final EmbeddingSourceTextResolver resolver = new EmbeddingSourceTextResolver(
            jobRepository,
            candidateProfileRepository,
            textBuilder,
            textNormalizer,
            sourceTextHasher
    );

    @Test
    void resolvesJobTextAndHash() {
        Job job = new Job();
        job.setId(42L);
        job.setTitle("Backend Java Developer");
        job.setDescription("Build APIs with Spring Boot.");
        job.setRequirementsText("PostgreSQL");
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));

        EmbeddingResolvedSourceText result = resolver.resolve(embedding(DocumentEmbeddingOwnerType.JOB, 42L));

        assertFalse(result.normalizedText().isBlank());
        assertEquals(sourceTextHasher.sha256Hex(result.normalizedText()), result.sourceTextHash());
    }

    @Test
    void resolvesProfileTextWithoutUsingName() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(7L);
        profile.setName("Ada Lovelace");
        profile.setCvText("Java developer with PostgreSQL experience.");
        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        EmbeddingResolvedSourceText result = resolver.resolve(embedding(DocumentEmbeddingOwnerType.PROFILE, 7L));

        assertEquals("CV:\nJava developer with PostgreSQL experience.", result.normalizedText());
        assertEquals(sourceTextHasher.sha256Hex(result.normalizedText()), result.sourceTextHash());
    }

    private static DocumentEmbedding embedding(DocumentEmbeddingOwnerType ownerType, Long ownerId) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setOwnerType(ownerType);
        embedding.setOwnerId(ownerId);
        return embedding;
    }
}
