package com.DataLaburo.web.controller;

import com.DataLaburo.web.analysis.CompatibilityBucket;
import com.DataLaburo.web.analysis.CompatibilityCategory;
import com.DataLaburo.web.analysis.CompatibilityConfidence;
import com.DataLaburo.web.analysis.EvidenceLevel;
import com.DataLaburo.web.analysis.RerankSignal;
import com.DataLaburo.web.analysis.RerankSignalPolarity;
import com.DataLaburo.web.analysis.SkillEquivalenceSignal;
import com.DataLaburo.web.analysis.TransferStrength;
import com.DataLaburo.web.analysis.TransferableSkill;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResponse;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityService;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.ProfileImprovementSuggestionService;
import com.DataLaburo.web.service.ProfileRoadmapSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.ConcurrentModel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileVectorCompatibilityControllerTest {
    private final CandidateProfileService candidateProfileService = mock(CandidateProfileService.class);
    private final CandidateProfileProjectService candidateProfileProjectService = mock(CandidateProfileProjectService.class);
    private final ProfileImprovementSuggestionService profileImprovementSuggestionService =
            new ProfileImprovementSuggestionService();
    private final ProfileRoadmapSuggestionService profileRoadmapSuggestionService =
            new ProfileRoadmapSuggestionService();
    private final VectorFirstCompatibilityService compatibilityService = mock(VectorFirstCompatibilityService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider = mock(ObjectProvider.class);
    private final ProfileVectorCompatibilityController controller = new ProfileVectorCompatibilityController(
            candidateProfileService,
            candidateProfileProjectService,
            profileImprovementSuggestionService,
            profileRoadmapSuggestionService,
            compatibilityServiceProvider
    );

    @Test
    void mapsRequirementChecklistWithoutPromotingTransferableSignalsToPresent() throws Exception {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java backend profile");
        profile.setHeadline("Backend Java junior");
        profile.setSummary("Builds APIs and documents project evidence.");
        profile.setDeclaredSkillsText("Java, Spring Boot");
        CandidateProfileProject project = project(profile, "Portfolio API");
        DocumentEmbedding embedding = profileEmbedding(1L, DocumentEmbeddingStatus.READY);

        VectorFirstCompatibilityResult result = new VectorFirstCompatibilityResult(
                14L,
                "Backend Engineer",
                "Example",
                1,
                0.68d,
                1,
                "BACKEND",
                "MID",
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                List.of("Java", "Spring Boot"),
                List.of("Kubernetes"),
                List.of("AWS"),
                List.of(new TransferableSkill(
                        "Docker",
                        "Kubernetes",
                        TransferStrength.PARTIAL,
                        "Base de contenedores transferible"
                )),
                List.of(
                        "Profundizar Kubernetes basico",
                        "Practicar despliegues",
                        "Documentar evidencia",
                        "Sugerencia extra no visible"
                ),
                "La oferta esta cerca semanticamente.",
                CompatibilityConfidence.MEDIUM,
                CompatibilityBucket.GOOD_WITH_MINOR_GAPS,
                1,
                0,
                List.of(
                        "Bajaria por rol periferico: QA.",
                        "Bajaria por seniority superior: oferta SENIOR vs objetivo JUNIOR.",
                        "Bajaria porque los matches genericos no son suficientes: Java."
                ),
                List.of("Posible deteccion dudosa de rol: titulo Android clasificado como QA."),
                List.of(new RerankSignal(
                        "ROLE_ALIGNED",
                        RerankSignalPolarity.POSITIVE,
                        "Rol alineado."
                ))
        ).withSkillEquivalenceSignals(List.of(new SkillEquivalenceSignal(
                "PostgreSQL",
                "SQL",
                "PARTIAL_EQUIVALENCE",
                "Base relacional relacionada"
        )));
        VectorFirstCompatibilityResult secondResult = new VectorFirstCompatibilityResult(
                15L,
                "Platform Engineer",
                "Example",
                2,
                0.64d,
                2,
                "DEVOPS",
                "MID",
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                List.of("Java"),
                List.of("Kubernetes"),
                List.of(),
                List.of(),
                List.of(),
                "Otra oferta cercana.",
                CompatibilityConfidence.MEDIUM,
                CompatibilityBucket.GOOD_WITH_MINOR_GAPS,
                2,
                0,
                List.of(),
                List.of(),
                List.of()
        );

        when(candidateProfileService.findById(1L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(1L)).thenReturn(Optional.of(embedding));
        when(candidateProfileProjectService.findByProfileId(1L)).thenReturn(List.of(project));
        when(compatibilityServiceProvider.getIfAvailable()).thenReturn(compatibilityService);
        when(compatibilityService.analyze(1L, 20)).thenReturn(new VectorFirstCompatibilityResponse(
                1L,
                "BAAI/bge-m3",
                1024,
                new VectorFirstCompatibilityResponse.Retrieval(20, "VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC"),
                List.of(result, secondResult)
        ));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.vectorFirstCompatibility(1L, "20", model);

        assertEquals("profile-vector-compatibility", viewName);
        assertEquals(embedding, model.getAttribute("profileEmbedding"));
        assertEquals(List.of(project), model.getAttribute("profileProjects"));
        List<?> rows = (List<?>) model.getAttribute("results");
        Object checklist = invoke(rows.get(0), "requirementChecklist");

        assertEquals(List.of("Java", "Spring Boot"), invoke(checklist, "presentSkills"));
        assertEquals(List.of("Kubernetes"), invoke(checklist, "missingCriticalSkills"));
        assertEquals(List.of("AWS"), invoke(checklist, "missingSecondarySkills"));

        List<?> transferableSkills = (List<?>) invoke(checklist, "transferableSkills");
        assertEquals(1, transferableSkills.size());
        assertEquals("Docker", invoke(transferableSkills.get(0), "from"));
        assertEquals("Kubernetes", invoke(transferableSkills.get(0), "to"));

        List<?> partialRelations = (List<?>) invoke(checklist, "partialRelations");
        assertEquals(1, partialRelations.size());
        assertEquals("PostgreSQL", invoke(partialRelations.get(0), "candidateSkill"));
        assertEquals("SQL", invoke(partialRelations.get(0), "targetSkill"));

        assertEquals(List.of(
                "Profundizar Kubernetes basico",
                "Practicar despliegues",
                "Documentar evidencia"
        ), invoke(checklist, "suggestions"));

        List<?> suggestions = (List<?>) invoke(rows.get(0), "improvementSuggestions");
        assertEquals(3, suggestions.size());
        assertEquals("LEARNING_GAP", invoke(suggestions.get(0), "category"));
        List<?> reasons = (List<?>) invoke(rows.get(0), "rerankReasons");
        assertEquals("Rol periferico", invoke(reasons.get(0), "label"));
        assertEquals("Seniority superior al objetivo", invoke(reasons.get(1), "label"));
        assertEquals("Pocas coincidencias directas", invoke(reasons.get(2), "label"));
        assertEquals("Bajaria por rol periferico: QA.", invoke(reasons.get(0), "detail"));
        List<?> warnings = (List<?>) invoke(rows.get(0), "rerankWarnings");
        assertEquals("Deteccion con baja confianza", invoke(warnings.get(0), "label"));
        List<?> signals = (List<?>) invoke(rows.get(0), "rerankSignals");
        assertEquals("Rol alineado", invoke(signals.get(0), "label"));
        List<?> roadmaps = (List<?>) model.getAttribute("profileRoadmaps");
        assertEquals(1, roadmaps.size());
        assertEquals("Kubernetes", invoke(roadmaps.get(0), "skillOrFamily"));
        verify(compatibilityService).analyze(1L, 20);
        verify(candidateProfileProjectService).findByProfileId(1L);
    }

    private static CandidateProfileProject project(CandidateProfile profile, String title) {
        CandidateProfileProject project = new CandidateProfileProject();
        project.setCandidateProfile(profile);
        project.setTitle(title);
        project.setDescription("Project evidence");
        project.setSkillsText("Java, Spring Boot");
        project.setEvidenceType(ProjectEvidenceType.PERSONAL_PROJECT);
        return project;
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

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
