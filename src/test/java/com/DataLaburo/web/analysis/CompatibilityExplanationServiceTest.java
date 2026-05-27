package com.DataLaburo.web.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatibilityExplanationServiceTest {
    private final CompatibilityExplanationService service = new CompatibilityExplanationService();

    @Test
    void assignsStrongMatchWhenDirectEvidenceHasNoCriticalGaps() {
        GapAnalysis gap = new GapAnalysis(
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of(),
                List.of("Kubernetes"),
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of("Java", "Spring Boot"),
                List.of("Kubernetes")
        );

        CompatibilityCategory category = service.assignCategory(
                gap,
                List.of(),
                EvidenceLevel.PROJECT,
                0.68d,
                true
        );

        assertEquals(CompatibilityCategory.STRONG_MATCH, category);
    }

    @Test
    void assignsTransferableOpportunityWhenGapHasDefensibleTransfer() {
        GapAnalysis gap = new GapAnalysis(
                List.of("Docker"),
                List.of("Kubernetes"),
                List.of(),
                List.of("Docker"),
                List.of("Kubernetes"),
                List.of()
        );

        CompatibilityCategory category = service.assignCategory(
                gap,
                List.of(new TransferableSkill(
                        "Docker",
                        "Kubernetes",
                        TransferStrength.PARTIAL,
                        "base de contenedores transferible"
                )),
                EvidenceLevel.WORK_EXPERIENCE,
                0.61d,
                true
        );

        assertEquals(CompatibilityCategory.TRANSFERABLE_OPPORTUNITY, category);
    }

    @Test
    void deduplicatesRoadmapSuggestionsForTransferAliases() {
        GapAnalysis gap = new GapAnalysis(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        CompatibilityExplanation explanation = service.explain(
                "Proyecto backend",
                0.62d,
                gap,
                List.of(
                        new TransferableSkill("Backend Development", "Cloud", TransferStrength.PARTIAL, "base backend"),
                        new TransferableSkill("Backend Developer", "Cloud", TransferStrength.PARTIAL, "base backend")
                ),
                null,
                null
        );

        assertEquals(List.of("Convertir Backend en practica concreta de Cloud"), explanation.roadmapSuggestions());
    }
}
