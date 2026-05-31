package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileProjectForm;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.repository.CandidateProfileProjectRepository;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProfileProjectServiceTest {
    private final CandidateProfileRepository candidateProfileRepository = mock(CandidateProfileRepository.class);
    private final CandidateProfileProjectRepository projectRepository = mock(CandidateProfileProjectRepository.class);
    private final CandidateProfileProjectService service = new CandidateProfileProjectService(
            candidateProfileRepository,
            projectRepository
    );

    @Test
    void createsProjectEvidenceForExistingProfile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(7L);

        CandidateProfileProjectForm form = new CandidateProfileProjectForm();
        form.setTitle(" API REST de ofertas laborales ");
        form.setDescription(" Backend Java con PostgreSQL. ");
        form.setSkillsText(" Java, Spring Boot, SQL ");
        form.setEvidenceType(ProjectEvidenceType.ACADEMIC_PROJECT);
        form.setRepositoryUrl(" ");
        form.setDemoUrl("https://demo.example");

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(projectRepository.save(any(CandidateProfileProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<CandidateProfileProject> result = service.create(7L, form);

        assertTrue(result.isPresent());
        CandidateProfileProject project = result.get();
        assertSame(profile, project.getCandidateProfile());
        assertEquals("API REST de ofertas laborales", project.getTitle());
        assertEquals("Backend Java con PostgreSQL.", project.getDescription());
        assertEquals("Java, Spring Boot, SQL", project.getSkillsText());
        assertEquals(ProjectEvidenceType.ACADEMIC_PROJECT, project.getEvidenceType());
        assertNull(project.getRepositoryUrl());
        assertEquals("https://demo.example", project.getDemoUrl());
        verify(projectRepository).save(project);
    }

    @Test
    void doesNotCreateProjectWhenProfileDoesNotExist() {
        CandidateProfileProjectForm form = new CandidateProfileProjectForm();
        form.setTitle("Portfolio API");
        form.setDescription("API backend.");

        when(candidateProfileRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.create(404L, form).isEmpty());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void requiresTitleAndDescription() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(7L);
        CandidateProfileProjectForm form = new CandidateProfileProjectForm();
        form.setTitle("Portfolio API");

        when(candidateProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(7L, form));

        assertEquals("Ingresa una descripcion breve del proyecto.", error.getMessage());
    }

    @Test
    void listsProjectsByProfile() {
        CandidateProfileProject project = new CandidateProfileProject();
        when(projectRepository.findByCandidateProfileIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(project));

        assertEquals(List.of(project), service.findByProfileId(7L));
    }

    @Test
    void deletesOnlyProjectsOwnedByProfile() {
        CandidateProfileProject project = new CandidateProfileProject();
        when(projectRepository.findByIdAndCandidateProfileId(10L, 7L)).thenReturn(Optional.of(project));

        assertTrue(service.delete(7L, 10L));
        verify(projectRepository).delete(project);

        assertFalse(service.delete(7L, 99L));
    }
}
