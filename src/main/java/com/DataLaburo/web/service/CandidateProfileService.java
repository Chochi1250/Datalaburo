package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileForm;
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

    public CandidateProfileService(CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
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

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? defaultValue : cleaned;
    }
}
