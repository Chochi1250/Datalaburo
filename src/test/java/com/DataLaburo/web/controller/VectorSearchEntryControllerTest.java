package com.DataLaburo.web.controller;

import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VectorSearchEntryControllerTest {
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final VectorSearchEntryController controller = new VectorSearchEntryController(candidateProfileService);
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
        String viewName = controller.vectorSearch(model);

        assertEquals("vector-search", viewName);
        Object rawOptions = model.getAttribute("profileOptions");
        assertNotNull(rawOptions);
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
    void postVectorSearchWithReadyProfileRedirectsToVectorFirstCompatibility() throws Exception {
        CandidateProfile profile = profile(7L, "Ready profile");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L))
                .thenReturn(Optional.of(profileEmbedding(7L, DocumentEmbeddingStatus.READY)));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7/vector-first-compatibility?limit=20"));

        verify(candidateProfileService).findById(7L);
        verify(candidateProfileService).findProfileEmbedding(7L);
        verifyNoMoreInteractions(candidateProfileService);
    }

    @Test
    void postVectorSearchWithPendingProfileRedirectsToProfileDetailWithClearMessage() throws Exception {
        CandidateProfile profile = profile(7L, "Pending profile");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L))
                .thenReturn(Optional.of(profileEmbedding(7L, DocumentEmbeddingStatus.PENDING)));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessError", containsString("embedding pendiente")));
    }

    @Test
    void postVectorSearchWithFailedProfileRedirectsToProfileDetailWithClearMessage() throws Exception {
        CandidateProfile profile = profile(7L, "Failed profile");
        DocumentEmbedding embedding = profileEmbedding(7L, DocumentEmbeddingStatus.FAILED);
        embedding.setErrorMessage("embedding-service unavailable");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.of(embedding));

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessError", containsString("fallo")));
    }

    @Test
    void postVectorSearchWithMissingEmbeddingRedirectsToProfileDetailWithClearMessage() throws Exception {
        CandidateProfile profile = profile(7L, "Profile without embedding");
        when(candidateProfileService.findById(7L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(7L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/vector-search").param("profileId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profiles/7"))
                .andExpect(flash().attribute("embeddingProcessError", containsString("todavia no tiene embedding")));
    }

    @Test
    void postVectorSearchWithUnknownProfileRedirectsBackWithError() throws Exception {
        when(candidateProfileService.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/vector-search").param("profileId", "404"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vector-search"))
                .andExpect(flash().attribute("vectorSearchError", "No se encontro el perfil seleccionado."));
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
}
