package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingRepository;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CandidateProfileService {
    private static final String DEFAULT_TARGET_ROLE = "UNDECIDED";
    private static final String DEFAULT_TARGET_SENIORITY = "ANY";
    private static final String DEFAULT_SEARCH_MODE = "FOCUSED";

    private final CandidateProfileRepository candidateProfileRepository;
    private final DocumentEmbeddingRepository documentEmbeddingRepository;
    private final EmbeddingPreparationService embeddingPreparationService;

    public CandidateProfileService(
            CandidateProfileRepository candidateProfileRepository,
            DocumentEmbeddingRepository documentEmbeddingRepository,
            EmbeddingPreparationService embeddingPreparationService
    ) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.documentEmbeddingRepository = documentEmbeddingRepository;
        this.embeddingPreparationService = embeddingPreparationService;
    }

    @Transactional(readOnly = true)
    public List<CandidateProfile> findAll() {
        return candidateProfileRepository.findAllByOrderByUpdatedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Optional<CandidateProfile> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return candidateProfileRepository.findById(id);
    }

    @Transactional
    public CandidateProfile create(CandidateProfileForm form) {
        CandidateProfile profile = new CandidateProfile();
        profile.setName(clean(form.getName()));
        profile.setCvText(clean(form.getCvText()));
        profile.setTargetRole(defaultIfBlank(form.getTargetRole(), DEFAULT_TARGET_ROLE));
        profile.setTargetSeniority(defaultIfBlank(form.getTargetSeniority(), DEFAULT_TARGET_SENIORITY));
        profile.setSearchMode(defaultIfBlank(form.getSearchMode(), DEFAULT_SEARCH_MODE));
        return candidateProfileRepository.save(profile);
    }

    @Transactional
    public Optional<CvTextUpdateResult> updateCvText(Long profileId, String newCvText) {
        if (profileId == null) {
            return Optional.empty();
        }

        String cleanedCvText = clean(newCvText);
        if (cleanedCvText.isBlank()) {
            throw new IllegalArgumentException("Pega el CV del perfil como texto.");
        }

        return candidateProfileRepository.findById(profileId)
                .map(profile -> updateCvText(profile, cleanedCvText));
    }

    @Transactional(readOnly = true)
    public Optional<DocumentEmbedding> findProfileEmbedding(Long profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return documentEmbeddingRepository
                .findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndEmbeddingDimensionsAndNormalizerVersion(
                        DocumentEmbeddingOwnerType.PROFILE,
                        profileId,
                        DocumentEmbeddingSectionType.FULL_TEXT,
                        DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                        DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS,
                        EmbeddingTextNormalizer.VERSION
                );
    }

    private CvTextUpdateResult updateCvText(CandidateProfile profile, String cleanedCvText) {
        if (clean(profile.getCvText()).equals(cleanedCvText)) {
            return CvTextUpdateResult.unchanged(profile);
        }

        profile.setCvText(cleanedCvText);
        CandidateProfile saved = candidateProfileRepository.save(profile);
        EmbeddingPreparationService.PreparationResult preparationResult =
                embeddingPreparationService.prepareCandidateProfile(saved);
        return CvTextUpdateResult.updated(saved, preparationResult);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? defaultValue : cleaned;
    }

    public enum CvTextUpdateAction {
        UPDATED,
        UNCHANGED
    }

    public record CvTextUpdateResult(
            CvTextUpdateAction action,
            CandidateProfile profile,
            EmbeddingPreparationService.PreparationResult preparationResult
    ) {
        public static CvTextUpdateResult updated(
                CandidateProfile profile,
                EmbeddingPreparationService.PreparationResult preparationResult
        ) {
            return new CvTextUpdateResult(CvTextUpdateAction.UPDATED, profile, preparationResult);
        }

        public static CvTextUpdateResult unchanged(CandidateProfile profile) {
            return new CvTextUpdateResult(CvTextUpdateAction.UNCHANGED, profile, null);
        }

        public boolean changed() {
            return action == CvTextUpdateAction.UPDATED;
        }
    }
}
