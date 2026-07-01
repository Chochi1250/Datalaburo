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
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.CvDocumentExtractionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ConcurrentModel;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VectorSearchEntryControllerTest {
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final BgeM3EmbeddingProcessingService bgeM3EmbeddingProcessingService = mock(BgeM3EmbeddingProcessingService.class);
    private final CvDocumentExtractionService cvDocumentExtractionService = mock(CvDocumentExtractionService.class);
    private final VectorSearchEntryController controller = new VectorSearchEntryController(
            candidateProfileService,
            bgeM3EmbeddingProcessingService,
            cvDocumentExtractionService
    );
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .build();

    @Test
    void getVectorSearchListsProfilesWithEmbeddingStatus() throws Exception {
        CandidateProfile ready = profile(1L, "Ready profile");
        ready.setHeadline("Backend Java");
        CandidateProfile pending = profile(2L, "Pending profile");
        when(candidateProfileService.findAll()).thenReturn(List.of(ready, pending));
        when(candidateProfileService.findProfileEmbedding(1L))
                .thenReturn(Optional.of(profileEmbedding(1L, DocumentEmbeddingStatus.READY)));
        when(candidateProfileService.findProfileEmbedding(2L))
                .thenReturn(Optional.of(profileEmbedding(2L, DocumentEmbeddingStatus.PENDING)));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.vectorSearch(model, new MockHttpSession());

        assertEquals("vector-search", viewName);
        Object rawOptions = model.getAttribute("profileOptions");
        assertNotNull(rawOptions);
        assertNotNull(model.getAttribute("createForm"));
        assertEquals("quick", model.getAttribute("activeVectorSearchFlow"));
        @SuppressWarnings("unchecked")
        List<VectorSearchEntryController.ProfileVectorSearchOption> options =
                (List<VectorSearchEntryController.ProfileVectorSearchOption>) rawOptions;
        assertEquals(2, options.size());
        assertEquals("Ready profile", options.get(0).getName());
        assertEquals("Backend Java", options.get(0).getHeadline());
        assertEquals("READY", options.get(0).getStatusLabel());
        assertTrue(options.get(0).isReady());
        assertEquals("Pending profile", options.get(1).getName());
        assertEquals("PENDING", options.get(1).getStatusLabel());
        assertFalse(options.get(1).isReady());
    }

    @Test
    void getVectorSearchShowsOnlyActiveSessionProfileWhenAvailable() throws Exception {
        CandidateProfile active = profile(8L, "Active profile");
        active.setHeadline("Data analyst");
        when(candidateProfileService.findAll()).thenReturn(List.of(active));
        when(candidateProfileService.findById(8L)).thenReturn(Optional.of(active));
        when(candidateProfileService.findProfileEmbedding(8L))
                .thenReturn(Optional.of(profileEmbedding(8L, DocumentEmbeddingStatus.READY)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("vectorSearchActiveProfileId", 8L);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.vectorSearch(model, session);

        assertEquals("vector-search", viewName);
        Object rawActiveProfile = model.getAttribute("activeProfile");
        assertNotNull(rawActiveProfile);
        VectorSearchEntryController.ProfileVectorSearchOption activeProfile =
                (VectorSearchEntryController.ProfileVectorSearchOption) rawActiveProfile;
        assertEquals("Active profile", activeProfile.getName());
        assertTrue(activeProfile.isReady());
    }

    @Test
    void postVectorSearchWithReadyProfileRedirectsToVectorFirstCompatibility() throws Exception {
        CandidateProfile profile = profile(7L, "Ready profile");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L))
                .thenReturn(Optional.of(profileEmbedding(7L, DocumentEmbeddingStatus.READY)));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7/vector-first-compatibility?limit=50"))
                .andExpect(request().sessionAttribute("vectorSearchActiveProfileId", 7L));

        verify(candidateProfileService).findById(7L);
        verify(candidateProfileService).findProfileEmbedding(7L);
        verifyNoMoreInteractions(candidateProfileService);
        verifyNoMoreInteractions(bgeM3EmbeddingProcessingService);
    }

    @Test
    void postVectorSearchWithPendingProfileProcessesAndRedirectsToVectorFirstCompatibility() throws Exception {
        CandidateProfile profile = profile(7L, "Pending profile");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));
        when(bgeM3EmbeddingProcessingService.processById(embedding.getId()))
                .thenReturn(Optional.of(readyProcessingResult(embedding)));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7/vector-first-compatibility?limit=50"))
                .andExpect(request().sessionAttribute("vectorSearchActiveProfileId", 7L));
    }

    @Test
    void postVectorSearchWithFailedProfileReportsProcessingError() throws Exception {
        CandidateProfile profile = profile(7L, "Failed profile");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.FAILED);
        embedding.setErrorMessage("embedding-service unavailable");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));
        when(bgeM3EmbeddingProcessingService.resetFailedById(embedding.getId()))
                .thenReturn(Optional.of(new EmbeddingProcessingResult(
                        EmbeddingProcessingAction.SKIPPED,
                        embedding.getId(),
                        embedding.getEmbeddingModel(),
                        embedding.getEmbeddingDimensions(),
                        "FAILED BAAI/bge-m3 embedding reset to PENDING; run process-bge-m3 again"
                )));
        when(bgeM3EmbeddingProcessingService.processById(embedding.getId()))
                .thenReturn(Optional.of(new EmbeddingProcessingResult(
                        EmbeddingProcessingAction.FAILED,
                        embedding.getId(),
                        embedding.getEmbeddingModel(),
                        embedding.getEmbeddingDimensions(),
                        "embedding-service unavailable"
                )));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vector-search#flujo-activo"))
                .andExpect(flash().attribute("vectorSearchError", containsString("No se pudo procesar")));
    }

    @Test
    void postVectorSearchWithMissingEmbeddingPreparesProcessesAndRedirectsToVectorFirstCompatibility() throws Exception {
        CandidateProfile profile = profile(7L, "Profile without embedding");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.PENDING);
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());
        when(candidateProfileService.prepareProfileEmbedding(7L))
                .thenReturn(Optional.of(preparationResult(EmbeddingPreparationService.PreparationAction.CREATED, embedding)));
        when(bgeM3EmbeddingProcessingService.processById(embedding.getId()))
                .thenReturn(Optional.of(readyProcessingResult(embedding)));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7/vector-first-compatibility?limit=50"))
                .andExpect(request().sessionAttribute("vectorSearchActiveProfileId", 7L));
    }

    @Test
    void postVectorSearchWithUnknownProfileRedirectsBackWithError() throws Exception {
        when(candidateProfileService.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/vector-search").param("profileId", "404"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vector-search"))
                .andExpect(flash().attribute("vectorSearchError", "No se encontro el perfil seleccionado."))
                .andExpect(flash().attribute("activeVectorSearchFlow", "existing"));
    }

    @Test
    void createProfileFromCvCreatesProfileProcessesEmbeddingAndRedirectsToVectorFirstCompatibility() throws Exception {
        CandidateProfile profile = profile(11L, "Quick profile");
        DocumentEmbedding embedding = profileEmbedding(11L, DocumentEmbeddingStatus.PENDING);
        when(candidateProfileService.create(any(CandidateProfileForm.class))).thenReturn(profile);
        when(candidateProfileService.findProfileEmbedding(11L)).thenReturn(Optional.empty());
        when(candidateProfileService.prepareProfileEmbedding(11L))
                .thenReturn(Optional.of(preparationResult(EmbeddingPreparationService.PreparationAction.CREATED, embedding)));
        when(bgeM3EmbeddingProcessingService.processById(embedding.getId()))
                .thenReturn(Optional.of(readyProcessingResult(embedding)));

        mockMvc.perform(post("/vector-search/cv")
                        .param("name", "Quick profile")
                        .param("headline", "Backend Java")
                        .param("cvText", "CV text with enough context"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/11/vector-first-compatibility?limit=50"))
                .andExpect(request().sessionAttribute("vectorSearchActiveProfileId", 11L));
    }

    @Test
    void extractCvTextReturnsExtractedTextForPreviewWithoutCreatingProfile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.pdf",
                "application/pdf",
                "binary-pdf".getBytes()
        );
        when(cvDocumentExtractionService.extractText(any(MultipartFile.class)))
                .thenReturn("Texto extraido para revisar antes de iniciar.");

        mockMvc.perform(multipart("/vector-search/cv/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("cv.pdf"))
                .andExpect(jsonPath("$.text").value("Texto extraido para revisar antes de iniciar."));

        verify(cvDocumentExtractionService).extractText(any(MultipartFile.class));
        verifyNoMoreInteractions(candidateProfileService);
        verifyNoMoreInteractions(bgeM3EmbeddingProcessingService);
    }

    @Test
    void extractCvTextReturnsClearErrorForInvalidDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.txt",
                "text/plain",
                "plain text".getBytes()
        );
        when(cvDocumentExtractionService.extractText(any(MultipartFile.class)))
                .thenThrow(new CvDocumentExtractionService.CvDocumentExtractionException("Formato no soportado. Sube un archivo PDF o DOCX."));

        mockMvc.perform(multipart("/vector-search/cv/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fileName").value("cv.txt"))
                .andExpect(jsonPath("$.error").value("Formato no soportado. Sube un archivo PDF o DOCX."));

        verify(cvDocumentExtractionService).extractText(any(MultipartFile.class));
        verifyNoMoreInteractions(candidateProfileService);
        verifyNoMoreInteractions(bgeM3EmbeddingProcessingService);
    }

    @Test
    void createProfileFromCvExtractsTextFromUploadedDocumentWhenManualTextIsEmpty() throws Exception {
        CandidateProfile profile = profile(12L, "Uploaded CV profile");
        DocumentEmbedding embedding = profileEmbedding(12L, DocumentEmbeddingStatus.PENDING);
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "binary-docx".getBytes()
        );
        when(cvDocumentExtractionService.extractText(any(MultipartFile.class)))
                .thenReturn("Texto extraido desde DOCX con Java y Spring Boot.");
        when(candidateProfileService.create(any(CandidateProfileForm.class))).thenReturn(profile);
        when(candidateProfileService.findProfileEmbedding(12L)).thenReturn(Optional.empty());
        when(candidateProfileService.prepareProfileEmbedding(12L))
                .thenReturn(Optional.of(preparationResult(EmbeddingPreparationService.PreparationAction.CREATED, embedding)));
        when(bgeM3EmbeddingProcessingService.processById(embedding.getId()))
                .thenReturn(Optional.of(readyProcessingResult(embedding)));

        mockMvc.perform(multipart("/vector-search/cv")
                        .file(file)
                        .param("name", "Uploaded CV profile")
                        .param("headline", "Backend Java")
                        .param("cvText", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/12/vector-first-compatibility?limit=50"))
                .andExpect(request().sessionAttribute("vectorSearchActiveProfileId", 12L));

        ArgumentCaptor<CandidateProfileForm> formCaptor = ArgumentCaptor.forClass(CandidateProfileForm.class);
        verify(candidateProfileService).create(formCaptor.capture());
        assertEquals("Texto extraido desde DOCX con Java y Spring Boot.", formCaptor.getValue().getCvText());
        verify(cvDocumentExtractionService).extractText(any(MultipartFile.class));
    }

    @Test
    void createProfileFromCvWithMissingCvRedirectsBackWithForm() throws Exception {
        mockMvc.perform(post("/vector-search/cv")
                        .param("name", "Quick profile")
                        .param("cvText", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vector-search#pegar-cv"))
                .andExpect(flash().attribute("vectorSearchError", containsString("Pega el CV")))
                .andExpect(flash().attribute("activeVectorSearchFlow", "quick"));
    }

    @Test
    void createProfileFromCvWithInvalidUploadedDocumentRedirectsBackWithExtractorMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "cvFile",
                "cv.txt",
                "text/plain",
                "plain text".getBytes()
        );
        when(cvDocumentExtractionService.extractText(any(MultipartFile.class)))
                .thenThrow(new CvDocumentExtractionService.CvDocumentExtractionException("Formato no soportado. Sube un archivo PDF o DOCX."));

        mockMvc.perform(multipart("/vector-search/cv")
                        .file(file)
                        .param("name", "Quick profile")
                        .param("cvText", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vector-search#pegar-cv"))
                .andExpect(flash().attribute("vectorSearchError", "Formato no soportado. Sube un archivo PDF o DOCX."))
                .andExpect(flash().attribute("activeVectorSearchFlow", "quick"));
    }

    private static CandidateProfile profile(Long id, String name) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setCvText("CV text with enough context");
        return profile;
    }

    private static DocumentEmbedding profileEmbedding(Long profileId, DocumentEmbeddingStatus status) {
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(100L + profileId);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.PROFILE);
        embedding.setOwnerId(profileId);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash("source-hash-" + profileId);
        embedding.setStatus(status);
        return embedding;
    }

    private static EmbeddingPreparationService.PreparationResult preparationResult(
            EmbeddingPreparationService.PreparationAction action,
            DocumentEmbedding embedding
    ) {
        return new EmbeddingPreparationService.PreparationResult(action, embedding, "source-hash", null);
    }

    private static EmbeddingProcessingResult readyProcessingResult(DocumentEmbedding embedding) {
        return new EmbeddingProcessingResult(
                EmbeddingProcessingAction.READY,
                embedding.getId(),
                embedding.getEmbeddingModel(),
                embedding.getEmbeddingDimensions(),
                null
        );
    }
}
