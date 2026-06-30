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
        applyVisibleMetadata(profile, form);
        return candidateProfileRepository.save(profile);
    }

    @Transactional
    public Optional<CandidateProfile> updateVisibleMetadata(Long profileId, CandidateProfileForm form) {
        if (profileId == null) {
            return Optional.empty();
        }
        return candidateProfileRepository.findById(profileId)
                .map(profile -> {
                    applyVisibleMetadata(profile, form);
                    return candidateProfileRepository.save(profile);
                });
    }

    @Transactional
    public Optional<CandidateProfile> updateFromForm(Long profileId, CandidateProfileForm form) {
        if (profileId == null) {
            return Optional.empty();
        }
        CandidateProfileForm safeForm = form == null ? new CandidateProfileForm() : form;
        String cleanedName = clean(safeForm.getName());
        if (cleanedName.isBlank()) {
            throw new IllegalArgumentException("Ingresa un nombre para el perfil.");
        }
        String cleanedCvText = clean(safeForm.getCvText());
        if (cleanedCvText.isBlank()) {
            throw new IllegalArgumentException("Pega el CV del perfil como texto.");
        }

        return candidateProfileRepository.findById(profileId)
                .map(profile -> {
                    boolean cvChanged = !clean(profile.getCvText()).equals(cleanedCvText);
                    profile.setName(cleanedName);
                    applyVisibleMetadata(profile, safeForm);
                    CandidateProfile saved = candidateProfileRepository.save(profile);
                    return cvChanged ? updateCvText(saved, cleanedCvText).profile() : saved;
                });
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

    @Transactional
    public Optional<EmbeddingPreparationService.PreparationResult> prepareProfileEmbedding(Long profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return candidateProfileRepository.findById(profileId)
                .map(embeddingPreparationService::prepareCandidateProfile);
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

    private void applyVisibleMetadata(CandidateProfile profile, CandidateProfileForm form) {
        if (profile == null) {
            return;
        }
        CandidateProfileForm safeForm = form == null ? new CandidateProfileForm() : form;
        profile.setHeadline(optionalText(safeForm.getHeadline()));
        profile.setSummary(optionalText(safeForm.getSummary()));
        profile.setDeclaredSkillsText(optionalText(safeForm.getDeclaredSkillsText()));
        profile.setLinkedinUrl(optionalText(safeForm.getLinkedinUrl()));
        profile.setGithubUrl(optionalText(safeForm.getGithubUrl()));
        profile.setPortfolioUrl(optionalText(safeForm.getPortfolioUrl()));
        profile.setAvatarPreset(optionalAvatarPreset(safeForm.getAvatarPreset()));
        profile.setTargetRole(defaultIfBlank(safeForm.getTargetRole(), DEFAULT_TARGET_ROLE));
        profile.setTargetSeniority(defaultIfBlank(safeForm.getTargetSeniority(), DEFAULT_TARGET_SENIORITY));
        profile.setSearchMode(defaultIfBlank(safeForm.getSearchMode(), DEFAULT_SEARCH_MODE));
    }

    private String optionalText(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String optionalAvatarPreset(String value) {
        String cleaned = clean(value).toLowerCase();
        return cleaned.matches("[a-z0-9][a-z0-9-]{0,63}") ? cleaned : null;
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
