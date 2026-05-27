package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import com.DataLaburo.web.repository.JobRepository;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingSourceTextResolver {
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingTextNormalizer textNormalizer;
    private final SourceTextHasher sourceTextHasher;

    public EmbeddingSourceTextResolver(
            JobRepository jobRepository,
            CandidateProfileRepository candidateProfileRepository,
            EmbeddingTextBuilder textBuilder,
            EmbeddingTextNormalizer textNormalizer,
            SourceTextHasher sourceTextHasher
    ) {
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.textBuilder = textBuilder;
        this.textNormalizer = textNormalizer;
        this.sourceTextHasher = sourceTextHasher;
    }

    public EmbeddingResolvedSourceText resolve(DocumentEmbedding documentEmbedding) {
        if (documentEmbedding == null) {
            throw new IllegalArgumentException("Document embedding is required");
        }
        if (documentEmbedding.getOwnerType() == null || documentEmbedding.getOwnerId() == null) {
            throw new IllegalArgumentException("Document embedding owner is required");
        }

        String rawText = switch (documentEmbedding.getOwnerType()) {
            case JOB -> resolveJobText(documentEmbedding.getOwnerId());
            case PROFILE -> resolveProfileText(documentEmbedding.getOwnerId());
        };
        String normalizedText = textNormalizer.normalize(rawText);
        if (normalizedText.isBlank()) {
            throw new IllegalStateException("Resolved source text is blank");
        }
        return new EmbeddingResolvedSourceText(normalizedText, sourceTextHasher.sha256Hex(normalizedText));
    }

    private String resolveJobText(Long ownerId) {
        Job job = jobRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Source JOB not found: " + ownerId));
        return textBuilder.buildForJob(job);
    }

    private String resolveProfileText(Long ownerId) {
        CandidateProfile profile = candidateProfileRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Source PROFILE not found: " + ownerId));
        return textBuilder.buildForCandidateProfile(profile);
    }
}
