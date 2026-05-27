package com.DataLaburo.web.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompatibilityAnalysisControllerTest {
    private final VectorFirstCompatibilityService service = mock(VectorFirstCompatibilityService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CompatibilityAnalysisController(service))
            .build();

    @Test
    void endpointRespondsWithExpectedStructure() throws Exception {
        when(service.analyze(1L, 20)).thenReturn(new VectorFirstCompatibilityResponse(
                1L,
                "BAAI/bge-m3",
                1024,
                new VectorFirstCompatibilityResponse.Retrieval(20, "VECTOR_FIRST_WITH_EXPLANATION"),
                List.of(new VectorFirstCompatibilityResult(
                        14L,
                        "Software Engineer Backend",
                        "Example",
                        1,
                        0.6806d,
                        1,
                        "BACKEND",
                        "MID",
                        CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                        EvidenceLevel.PROJECT,
                        List.of("Java", "Spring Boot", "PostgreSQL"),
                        List.of(),
                        List.of("Kubernetes"),
                        List.of(new TransferableSkill(
                                "Docker",
                                "Kubernetes",
                                TransferStrength.PARTIAL,
                                "base de contenedores transferible"
                        )),
                        List.of("Profundizar Kubernetes basico"),
                        "La oferta esta cerca semanticamente y comparte nucleo backend.",
                        CompatibilityConfidence.MEDIUM
                ))
        ));

        mockMvc.perform(get("/internal/analysis/profiles/1/vector-first-compatibility")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.embeddingModel").value("BAAI/bge-m3"))
                .andExpect(jsonPath("$.embeddingDimensions").value(1024))
                .andExpect(jsonPath("$.retrieval.strategy").value("VECTOR_FIRST_WITH_EXPLANATION"))
                .andExpect(jsonPath("$.results[0].jobId").value(14))
                .andExpect(jsonPath("$.results[0].vectorRank").value(1))
                .andExpect(jsonPath("$.results[0].analysisRank").value(1))
                .andExpect(jsonPath("$.results[0].compatibilityCategory").value("GOOD_MATCH_WITH_MINOR_GAPS"))
                .andExpect(jsonPath("$.results[0].transferableSkills[0].from").value("Docker"));

        verify(service).analyze(1L, 20);
    }
}
