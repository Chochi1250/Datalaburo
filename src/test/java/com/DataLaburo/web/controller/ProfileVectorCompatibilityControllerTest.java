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
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
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
    private final VectorFirstCompatibilityService compatibilityService = mock(VectorFirstCompatibilityService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider = mock(ObjectProvider.class);
    private final ProfileVectorCompatibilityController controller = new ProfileVectorCompatibilityController(
            candidateProfileService,
            compatibilityServiceProvider
    );

    @Test
    void mapsRequirementChecklistWithoutPromotingTransferableSignalsToPresent() throws Exception {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java backend profile");

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
                List.of(),
                List.of(),
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

        when(candidateProfileService.findById(1L)).thenReturn(Optional.of(profile));
        when(compatibilityServiceProvider.getIfAvailable()).thenReturn(compatibilityService);
        when(compatibilityService.analyze(1L, 20)).thenReturn(new VectorFirstCompatibilityResponse(
                1L,
                "BAAI/bge-m3",
                1024,
                new VectorFirstCompatibilityResponse.Retrieval(20, "VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC"),
                List.of(result)
        ));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.vectorFirstCompatibility(1L, "20", model);

        assertEquals("profile-vector-compatibility", viewName);
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
        verify(compatibilityService).analyze(1L, 20);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
