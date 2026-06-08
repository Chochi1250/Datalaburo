package com.DataLaburo.web.controller;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingPreparationService;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final CandidateProfileProjectService candidateProfileProjectService = mock(CandidateProfileProjectService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProfileController(candidateProfileService, candidateProfileProjectService))
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
        DocumentEmbedding embedding = new DocumentEmbedding();
        embedding.setId(100L);
        embedding.setOwnerType(DocumentEmbeddingOwnerType.PROFILE);
        embedding.setOwnerId(profileId);
        embedding.setSectionType(DocumentEmbeddingSectionType.FULL_TEXT);
        embedding.setEmbeddingModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
        embedding.setEmbeddingDimensions(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS);
        embedding.setNormalizerVersion(EmbeddingTextNormalizer.VERSION);
        embedding.setSourceTextHash("source-hash");
        embedding.setStatus(DocumentEmbeddingStatus.PENDING);
        return embedding;
    }
}
