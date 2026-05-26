package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class EmbeddingPreparationService {
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingTextNormalizer textNormalizer;
    private final SourceTextHasher sourceTextHasher;
    private final DocumentEmbeddingRepository documentEmbeddingRepository;

    public EmbeddingPreparationService(
            EmbeddingTextBuilder textBuilder,
            EmbeddingTextNormalizer textNormalizer,
            SourceTextHasher sourceTextHasher,
            DocumentEmbeddingRepository documentEmbeddingRepository
    ) {
        this.textBuilder = textBuilder;
        this.textNormalizer = textNormalizer;
        this.sourceTextHasher = sourceTextHasher;
        this.documentEmbeddingRepository = documentEmbeddingRepository;
    }

    @Transactional
    public PreparationResult prepareJob(Job job) {
        if (job == null || job.getId() == null) {
            return PreparationResult.skipped("Job id is required");
        }
        return prepare(DocumentEmbeddingOwnerType.JOB, job.getId(), textBuilder.buildForJob(job));
    }

    @Transactional
    public PreparationResult prepareCandidateProfile(CandidateProfile profile) {
        if (profile == null || profile.getId() == null) {
            return PreparationResult.skipped("Candidate profile id is required");
        }
        return prepare(DocumentEmbeddingOwnerType.PROFILE, profile.getId(), textBuilder.buildForCandidateProfile(profile));
    }

    private PreparationResult prepare(DocumentEmbeddingOwnerType ownerType, Long ownerId, String rawText) {
        String normalizedText = textNormalizer.normalize(rawText);
        if (normalizedText.isBlank()) {
            return PreparationResult.skipped("Source text is blank");
        }

        String sourceTextHash = sourceTextHasher.sha256Hex(normalizedText);
        DocumentEmbeddingSectionType sectionType = DocumentEmbeddingSectionType.FULL_TEXT;
        String embeddingModel = DocumentEmbedding.DEFAULT_EMBEDDING_MODEL;
        String normalizerVersion = EmbeddingTextNormalizer.VERSION;

        return documentEmbeddingRepository
                .findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
                        ownerType,
                        ownerId,
                        sectionType,
                        embeddingModel,
                        normalizerVersion
                )
                .map(existing -> updateIfChanged(existing, sourceTextHash))
                .orElseGet(() -> create(ownerType, ownerId, sectionType, embeddingModel, normalizerVersion, sourceTextHash));
    }

    private PreparationResult create(
            DocumentEmbeddingOwnerType ownerType,
            Long ownerId,
            DocumentEmbeddingSectionType sectionType,
            String embeddingModel,
            String normalizerVersion,
            String sourceTextHash
    ) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setOwnerType(ownerType);
        embedding.setOwnerId(ownerId);
        embedding.setSectionType(sectionType);
        embedding.setEmbeddingModel(embeddingModel);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(normalizerVersion);
        embedding.setSourceTextHash(sourceTextHash);
        embedding.setStatus(DocumentEmbeddingStatus.PENDING);
        embedding.setErrorMessage(null);
        embedding.setLastEmbeddedAt(null);

        DocumentEmbedding saved = documentEmbeddingRepository.save(embedding);
        return new PreparationResult(PreparationAction.CREATED, saved, sourceTextHash, null);
    }

    private PreparationResult updateIfChanged(DocumentEmbedding existing, String sourceTextHash) {
        if (Objects.equals(existing.getSourceTextHash(), sourceTextHash)) {
            return new PreparationResult(PreparationAction.UNCHANGED, existing, sourceTextHash, null);
        }

        existing.setSourceTextHash(sourceTextHash);
        existing.setStatus(DocumentEmbeddingStatus.PENDING);
        existing.setErrorMessage(null);
        existing.setLastEmbeddedAt(null);

        DocumentEmbedding saved = documentEmbeddingRepository.save(existing);
        return new PreparationResult(PreparationAction.UPDATED, saved, sourceTextHash, null);
    }

    public enum PreparationAction {
        CREATED,
        UPDATED,
        UNCHANGED,
        SKIPPED
    }

    public record PreparationResult(
            PreparationAction action,
            DocumentEmbedding documentEmbedding,
            String sourceTextHash,
            String reason
    ) {
        public static PreparationResult skipped(String reason) {
            return new PreparationResult(PreparationAction.SKIPPED, null, null, reason);
        }
    }
}
