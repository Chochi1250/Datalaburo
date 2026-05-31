package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileProjectForm;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.repository.CandidateProfileProjectRepository;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CandidateProfileProjectService {
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileProjectRepository projectRepository;

    public CandidateProfileProjectService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateProfileProjectRepository projectRepository
    ) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileProject> findByProfileId(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return projectRepository.findByCandidateProfileIdOrderByCreatedAtDescIdDesc(profileId);
    }

    @Transactional
    public Optional<CandidateProfileProject> create(Long profileId, CandidateProfileProjectForm form) {
        if (profileId == null || form == null) {
            return Optional.empty();
        }

        CandidateProfile profile = candidateProfileRepository.findById(profileId).orElse(null);
        if (profile == null) {
            return Optional.empty();
        }

        CandidateProfileProject project = new CandidateProfileProject();
        project.setCandidateProfile(profile);
        project.setTitle(required(form.getTitle(), "Ingresa un titulo para el proyecto."));
        project.setDescription(required(form.getDescription(), "Ingresa una descripcion breve del proyecto."));
        project.setSkillsText(clean(form.getSkillsText()));
        project.setEvidenceType(form.getEvidenceType() == null ? ProjectEvidenceType.OTHER : form.getEvidenceType());
        project.setRepositoryUrl(optionalText(form.getRepositoryUrl()));
        project.setDemoUrl(optionalText(form.getDemoUrl()));

        return Optional.of(projectRepository.save(project));
    }

    @Transactional
    public boolean delete(Long profileId, Long projectId) {
        if (profileId == null || projectId == null) {
            return false;
        }

        return projectRepository.findByIdAndCandidateProfileId(projectId, profileId)
                .map(project -> {
                    projectRepository.delete(project);
                    return true;
                })
                .orElse(false);
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String optionalText(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
