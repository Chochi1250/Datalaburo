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
        return candidateProfileRepository.save(profile);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
