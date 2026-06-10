package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
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
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvDocumentExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
