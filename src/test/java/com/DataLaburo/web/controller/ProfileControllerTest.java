package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.dto.CandidateProfileProjectForm;
import com.DataLaburo.web.embedding.BgeM3EmbeddingProcessingService;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingProcessingAction;
import com.DataLaburo.web.embedding.EmbeddingProcessingResult;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvDocumentExtractionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ProfileControllerTest {
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final CandidateProfileProjectService candidateProfileProjectService = mock(CandidateProfileProjectService.class);
    private final CvDocumentExtractionService cvDocumentExtractionService = mock(CvDocumentExtractionService.class);
    private final BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService = mock(BgeM3EmbeddingProcessingService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProfileController(
                    candidateProfileService,
                    candidateProfileProjectService,
                    cvDocumentExtractionService,
                    bgeM3EmbeddingProcessingService
            ))
            .build();

    @Test
    void newProfileConsumesDraftFromSessionForPrefill() throws Exception {
        CandidateProfileForm draft = new CandidateProfileForm();
        draft.setName("Perfil desde CV");
        draft.setHeadline("Backend Java");
        draft.setCvText("Texto temporal del CV");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("profileCreateDraft", draft);

        MvcResult result = mockMvc.perform(get("/profiles/new").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("profileDraftPrefilled", true))
                .andReturn();

        CandidateProfileForm form = (CandidateProfileForm) result.getModelAndView().getModel().get("form");
        assertEquals("Perfil desde CV", form.getName());
        assertEquals("Backend Java", form.getHeadline());
        assertEquals("Texto temporal del CV", form.getCvText());
        assertEquals("atlas", result.getModelAndView().getModel().get("selectedAvatarPreset"));
        assertNull(session.getAttribute("profileCreateDraft"));
    }

    @Test
    void newProfileExposesDefaultAvatarPresetAndInitialProjectForm() throws Exception {
        mockMvc.perform(get("/profiles/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("selectedAvatarPreset", "atlas"))
                .andExpect(model().attributeExists("projectForm"))
                .andExpect(model().attributeExists("projectForms"))
                .andExpect(model().attributeExists("evidenceTypes"));
    }

    @Test
    void postProfilePreservesSelectedAvatarPresetWhenReturningWithValidationError() throws Exception {
        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil Backend")
                        .param("avatarPreset", "java"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("selectedAvatarPreset", "java"))
                .andExpect(model().attribute("error", containsString("CV")));
    }

    @Test
    void postProfileBindsAvatarPresetIntoCompleteForm() throws Exception {
        CandidateProfile profile = profile(7L);
        ArgumentCaptor<CandidateProfileForm> captor = ArgumentCaptor.forClass(CandidateProfileForm.class);
        when(candidateProfileService.create(any(CandidateProfileForm.class))).thenReturn(profile);

        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil completo")
                        .param("cvText", "CV completo")
                        .param("avatarPreset", "java")
                        .param("headline", "Backend Java")
                        .param("summary", "APIs y servicios")
                        .param("declaredSkillsText", "Java, Spring Boot")
                        .param("linkedinUrl", "https://www.linkedin.com/in/example")
                        .param("githubUrl", "https://github.com/example")
                        .param("portfolioUrl", "https://example.dev")
                        .param("targetRole", "BACKEND")
                        .param("targetSeniority", "JUNIOR")
                        .param("searchMode", "FOCUSED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"));

        verify(candidateProfileService).create(captor.capture());
        CandidateProfileForm submitted = captor.getValue();
        assertEquals("java", submitted.getAvatarPreset());
        assertEquals("Backend Java", submitted.getHeadline());
        assertEquals("APIs y servicios", submitted.getSummary());
        assertEquals("Java, Spring Boot", submitted.getDeclaredSkillsText());
        assertEquals("https://www.linkedin.com/in/example", submitted.getLinkedinUrl());
        assertEquals("https://github.com/example", submitted.getGithubUrl());
        assertEquals("https://example.dev", submitted.getPortfolioUrl());
        assertEquals("BACKEND", submitted.getTargetRole());
        assertEquals("JUNIOR", submitted.getTargetSeniority());
        assertEquals("FOCUSED", submitted.getSearchMode());
    }

    @Test
    void editProfileReopensWithAllEditableFieldsPrefilled() throws Exception {
        CandidateProfile profile = profile(7L);
        profile.setAvatarPreset("kubernetes");
        profile.setHeadline("Cloud backend engineer");
        profile.setSummary("Builds backend services.");
        profile.setDeclaredSkillsText("Java, Kubernetes, PostgreSQL");
        profile.setLinkedinUrl("https://www.linkedin.com/in/example");
        profile.setGithubUrl("https://github.com/example");
        profile.setPortfolioUrl("https://example.dev");
        profile.setTargetRole("CLOUD");
        profile.setTargetSeniority("MID");
        profile.setSearchMode("EXPLORATORY");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));

        MvcResult result = mockMvc.perform(get("/profiles/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("selectedAvatarPreset", "kubernetes"))
                .andReturn();

        CandidateProfileForm form = (CandidateProfileForm) result.getModelAndView().getModel().get("form");
        assertEquals("Profile", form.getName());
        assertEquals("New CV", form.getCvText());
        assertEquals("kubernetes", form.getAvatarPreset());
        assertEquals("Cloud backend engineer", form.getHeadline());
        assertEquals("Builds backend services.", form.getSummary());
        assertEquals("Java, Kubernetes, PostgreSQL", form.getDeclaredSkillsText());
        assertEquals("https://www.linkedin.com/in/example", form.getLinkedinUrl());
        assertEquals("https://github.com/example", form.getGithubUrl());
        assertEquals("https://example.dev", form.getPortfolioUrl());
        assertEquals("CLOUD", form.getTargetRole());
        assertEquals("MID", form.getTargetSeniority());
        assertEquals("EXPLORATORY", form.getSearchMode());
    }

    @Test
    void editLegacyProfileUsesAvatarFallbackWithoutInventingPersistedPreset() throws Exception {
        CandidateProfile profile = profile(7L);
        profile.setAvatarPreset(null);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));

        MvcResult result = mockMvc.perform(get("/profiles/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("selectedAvatarPreset", "atlas"))
                .andReturn();

        CandidateProfileForm form = (CandidateProfileForm) result.getModelAndView().getModel().get("form");
        assertNull(form.getAvatarPreset());
    }

    @Test
    void newProfileUsesSupportedReturnToForCancelHref() throws Exception {
        mockMvc.perform(get("/profiles/new").param("returnTo", "/vector-search#flujo-activo"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("cancelHref", "/vector-search#flujo-activo"))
                .andExpect(model().attribute("returnTo", "/vector-search#flujo-activo"));
    }

    @Test
    void newProfileDraftStoresTemporaryFormWithoutCreatingProfile() throws Exception {
        mockMvc.perform(post("/profiles/new/draft")
                        .param("name", "Perfil completo")
                        .param("headline", "Data analyst")
                        .param("cvText", "CV temporal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/new"))
                .andExpect(request().sessionAttribute("profileCreateDraft", org.hamcrest.Matchers.any(CandidateProfileForm.class)));

        verify(candidateProfileService, never()).create(any());
    }

    @Test
    void newProfileDraftKeepsEncodedReturnToWhenRedirectingBack() throws Exception {
        mockMvc.perform(post("/profiles/new/draft")
                        .param("name", "Perfil completo")
                        .param("returnTo", "/matching?profileId=7&tab=summary"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/new?returnTo=/matching?profileId%3D7%26tab%3Dsummary"));
    }

    @Test
    void postProfileCreatesInitialProjectWhenProvided() throws Exception {
        CandidateProfile profile = profile(7L);
        when(candidateProfileService.create(any(CandidateProfileForm.class))).thenReturn(profile);

        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil Backend")
                        .param("cvText", "CV con experiencia backend")
                        .param("title", "API REST de ofertas")
                        .param("description", "Backend Java con Spring Boot, PostgreSQL, endpoints REST y tests.")
                        .param("skillsText", "Java, Spring Boot, PostgreSQL")
                        .param("evidenceType", "PERSONAL_PROJECT")
                        .param("repositoryUrl", "https://github.com/example/api"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7#projects"))
                .andExpect(flash().attribute("projectMessage", containsString("Proyecto inicial guardado")));

        verify(candidateProfileService).create(any(CandidateProfileForm.class));
        verify(candidateProfileProjectService).create(eq(7L), argThat((CandidateProfileProjectForm projectForm) ->
                "API REST de ofertas".equals(projectForm.getTitle())
                        && "Java, Spring Boot, PostgreSQL".equals(projectForm.getSkillsText())
                        && ProjectEvidenceType.PERSONAL_PROJECT == projectForm.getEvidenceType()
        ));
    }

    @Test
    void postProfileCreatesMultipleInitialProjectsWhenProvided() throws Exception {
        CandidateProfile profile = profile(9L);
        ArgumentCaptor<CandidateProfileProjectForm> captor = ArgumentCaptor.forClass(CandidateProfileProjectForm.class);
        when(candidateProfileService.create(any(CandidateProfileForm.class))).thenReturn(profile);

        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil Full stack")
                        .param("cvText", "CV con experiencia visible")
                        .param("avatarPreset", "aurora")
                        .param("projectTitles", "API REST de ofertas", "Panel de seguimiento")
                        .param("projectDescriptions",
                                "Backend Java con Spring Boot y PostgreSQL.",
                                "Dashboard con reportes y filtros.")
                        .param("projectSkillsTexts",
                                "Java, Spring Boot, PostgreSQL",
                                "React, TypeScript, APIs")
                        .param("projectEvidenceTypes", "PERSONAL_PROJECT", "WORK_PROJECT")
                        .param("projectRepositoryUrls",
                                "https://github.com/example/api",
                                "https://github.com/example/dashboard")
                        .param("projectDemoUrls", "", "https://demo.example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/9#projects"))
                .andExpect(flash().attribute("projectMessage", containsString("2 proyectos iniciales")));

        verify(candidateProfileProjectService, times(2)).create(eq(9L), captor.capture());
        List<CandidateProfileProjectForm> projectForms = captor.getAllValues();
        assertEquals("API REST de ofertas", projectForms.get(0).getTitle());
        assertEquals(ProjectEvidenceType.PERSONAL_PROJECT, projectForms.get(0).getEvidenceType());
        assertEquals("Panel de seguimiento", projectForms.get(1).getTitle());
        assertEquals(ProjectEvidenceType.WORK_PROJECT, projectForms.get(1).getEvidenceType());
    }

    @Test
    void postProfileWithPartialInitialProjectShowsErrorBeforeCreatingProfile() throws Exception {
        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil Backend")
                        .param("cvText", "CV con experiencia backend")
                        .param("title", "API REST de ofertas"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("error", containsString("descripcion breve")))
                .andExpect(model().attributeExists("projectForm"))
                .andExpect(model().attributeExists("evidenceTypes"));

        verify(candidateProfileService, never()).create(any());
        verify(candidateProfileProjectService, never()).create(any(), any());
    }

    @Test
    void postProfileWithPartialSecondProjectShowsIndexedErrorBeforeCreatingProfile() throws Exception {
        mockMvc.perform(post("/profiles")
                        .param("name", "Perfil Backend")
                        .param("cvText", "CV con experiencia backend")
                        .param("projectTitles", "API REST de ofertas", "Panel interno")
                        .param("projectDescriptions",
                                "Backend Java con Spring Boot, PostgreSQL y tests.",
                                "")
                        .param("projectSkillsTexts",
                                "Java, Spring Boot, PostgreSQL",
                                "React, TypeScript"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-new"))
                .andExpect(model().attribute("error", containsString("Proyecto 2")))
                .andExpect(model().attributeExists("projectForms"))
                .andExpect(model().attributeExists("evidenceTypes"));

        verify(candidateProfileService, never()).create(any());
        verify(candidateProfileProjectService, never()).create(any(), any());
    }

    @Test
    void postProfileCvRedirectsToProfileDetailAfterUpdate() throws Exception {
        CandidateProfile profile = profile(7L);
        DocumentEmbedding embedding = profileEmbedding(7L);
        EmbeddingPreparationService.PreparationResult preparationResult =
                new EmbeddingPreparationService.PreparationResult(
                        EmbeddingPreparationService.PreparationAction.UPDATED,
                        embedding,
                        "source-hash",
                        null
                );
        CandidateProfileService.CvTextUpdateResult updateResult =
                CandidateProfileService.CvTextUpdateResult.updated(profile, preparationResult);
        when(candidateProfileService.updateCvText(7L, "New CV")).thenReturn(Optional.of(updateResult));

        mockMvc.perform(post("/profiles/7/cv").param("cvText", "New CV"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("cvMessage", containsString("CV guardado")));
    }

    @Test
    void postProfileCvRedirectsWithErrorWhenTextIsBlank() throws Exception {
        when(candidateProfileService.updateCvText(7L, "   "))
                .thenThrow(new IllegalArgumentException("Pega el CV del perfil como texto."));

        mockMvc.perform(post("/profiles/7/cv").param("cvText", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("cvError", "Pega el CV del perfil como texto."));
    }

    @Test
    void uploadProfileCvShowsEditablePreviewWithoutSavingCvText() throws Exception {
        CandidateProfile profile = profile(7L);
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "binary-docx".getBytes()
        );

        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileProjectService.findByProfileId(7L)).thenReturn(List.of());
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());
        when(cvDocumentExtractionService.extractText(file)).thenReturn("Texto extraido del CV");

        mockMvc.perform(multipart("/profiles/7/cv/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-detail"))
                .andExpect(model().attribute("cvUploadPreviewText", "Texto extraido del CV"))
                .andExpect(model().attribute("cvUploadMessage", containsString("Texto extraido listo")));

        verify(candidateProfileService, never()).updateCvText(any(), any());
    }

    @Test
    void uploadProfileCvShowsExtractionErrorWithoutSavingCvText() throws Exception {
        CandidateProfile profile = profile(7L);
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.pdf",
                "application/pdf",
                "broken-pdf".getBytes()
        );

        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileProjectService.findByProfileId(7L)).thenReturn(List.of());
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());
        when(cvDocumentExtractionService.extractText(file)).thenThrow(
                new CvDocumentExtractionService.CvDocumentExtractionException("No se pudo extraer texto del archivo.")
        );

        mockMvc.perform(multipart("/profiles/7/cv/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-detail"))
                .andExpect(model().attribute("cvUploadError", "No se pudo extraer texto del archivo."));

        verify(candidateProfileService, never()).updateCvText(any(), any());
    }

    @Test
    void cancelPreviewReturnsToProfileDetailWithoutSavingCvText() throws Exception {
        CandidateProfile profile = profile(7L);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileProjectService.findByProfileId(7L)).thenReturn(List.of());
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/profiles/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-detail"))
                .andExpect(model().attributeDoesNotExist("cvUploadPreviewText"));

        verify(candidateProfileService, never()).updateCvText(any(), any());
    }

    @Test
    void profileDetailExposesPendingProfileEmbeddingForProcessAction() throws Exception {
        CandidateProfile profile = profile(7L);
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileProjectService.findByProfileId(7L)).thenReturn(List.of());
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));

        mockMvc.perform(get("/profiles/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile-detail"))
                .andExpect(model().attribute("profileEmbedding", embedding));
    }

    @Test
    void postProfileEmbeddingProcessProcessesSingleProfileEmbedding() throws Exception {
        CandidateProfile profile = profile(7L);
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        embedding.setId(100L);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));
        when(bgeM3EmbeddingProcessingService.processById(100L)).thenReturn(Optional.of(new EmbeddingProcessingResult(
                EmbeddingProcessingAction.READY,
                100L,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS,
                null
        )));

        mockMvc.perform(post("/profiles/7/embedding/process"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessMessage", containsString("READY")));

        verify(bgeM3EmbeddingProcessingService).processById(100L);
        verify(bgeM3EmbeddingProcessingService, never()).processPending(any());
    }

    @Test
    void postProfileEmbeddingProcessRedirectsWithErrorWhenProcessingFails() throws Exception {
        CandidateProfile profile = profile(7L);
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        embedding.setId(100L);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));
        when(bgeM3EmbeddingProcessingService.processById(100L)).thenReturn(Optional.of(new EmbeddingProcessingResult(
                EmbeddingProcessingAction.FAILED,
                100L,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS,
                "embedding-service unavailable"
        )));

        mockMvc.perform(post("/profiles/7/embedding/process"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessError", containsString("embedding-service unavailable")));

        verify(bgeM3EmbeddingProcessingService).processById(100L);
        verify(bgeM3EmbeddingProcessingService, never()).processPending(any());
    }

    @Test
    void postProfileEmbeddingProcessShowsClearMessageWhenCvIsBlank() throws Exception {
        CandidateProfile profile = profile(7L);
        profile.setCvText(" ");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/profiles/7/embedding/process"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessError", containsString("Primero guarda un CV textual")));

        verify(candidateProfileService, never()).findProfileEmbedding(any());
        verify(bgeM3EmbeddingProcessingService, never()).processById(any());
        verify(bgeM3EmbeddingProcessingService, never()).processPending(any());
    }

    @Test
    void postProfileEmbeddingProcessPreparesMissingProfileEmbeddingBeforeProcessing() throws Exception {
        CandidateProfile profile = profile(7L);
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        embedding.setId(100L);
        EmbeddingPreparationService.PreparationResult preparationResult =
                new EmbeddingPreparationService.PreparationResult(
                        EmbeddingPreparationService.PreparationAction.CREATED,
                        embedding,
                        "source-hash",
                        null
                );
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());
        when(candidateProfileService.prepareProfileEmbedding(7L)).thenReturn(Optional.of(preparationResult));
        when(bgeM3EmbeddingProcessingService.processById(100L)).thenReturn(Optional.of(new EmbeddingProcessingResult(
                EmbeddingProcessingAction.READY,
                100L,
                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS,
                null
        )));

        mockMvc.perform(post("/profiles/7/embedding/process"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessMessage", containsString("READY")));

        verify(candidateProfileService).prepareProfileEmbedding(7L);
        verify(bgeM3EmbeddingProcessingService).processById(100L);
        verify(bgeM3EmbeddingProcessingService, never()).processPending(any());
    }

    @Test
    void postProfileMetadataRedirectsToProfileDetailWithMessage() throws Exception {
        CandidateProfile profile = profile(7L);
        profile.setHeadline("Backend Java junior");
        when(candidateProfileService.updateVisibleMetadata(eq(7L), any(CandidateProfileForm.class)))
                .thenReturn(Optional.of(profile));

        mockMvc.perform(post("/profiles/7/metadata")
                        .param("headline", "Backend Java junior")
                        .param("summary", "Builds APIs.")
                        .param("declaredSkillsText", "Java, Spring Boot")
                        .param("targetRole", "BACKEND")
                        .param("targetSeniority", "JUNIOR")
                        .param("searchMode", "FOCUSED")
                        .param("linkedinUrl", "https://www.linkedin.com/in/example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("metadataMessage", containsString("Metadata visible guardada")));
    }

    private static CandidateProfile profile(Long id) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName("Profile");
        profile.setCvText("New CV");
        return profile;
    }

    private static DocumentEmbedding profileEmbedding(Long profileId) {
        return profileEmbedding(profileId, DocumentEmbeddingStatus.PENDING);
    }

    private static DocumentEmbedding profileEmbedding(Long profileId, DocumentEmbeddingStatus status) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(100L);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.PROFILE);
        embedding.setOwnerId(profileId);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash("source-hash");
        embedding.setStatus(status);
        return embedding;
    }
}
