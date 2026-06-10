package com.DataLaburo.web.service;

import com.DataLaburo.web.analysis.CompatibilityBucket;
import com.DataLaburo.web.analysis.CompatibilityCategory;
import com.DataLaburo.web.analysis.CompatibilityConfidence;
import com.DataLaburo.web.analysis.EvidenceLevel;
import com.DataLaburo.web.analysis.SkillEquivalenceSignal;
import com.DataLaburo.web.analysis.TransferStrength;
import com.DataLaburo.web.analysis.TransferableSkill;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileRoadmapSuggestionServiceTest {
    private final ProfileRoadmapSuggestionService service = new ProfileRoadmapSuggestionService();

    @Test
    void groupsRepeatedGapsFromTopN() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("Kubernetes"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("Kubernetes"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals(1, roadmaps.size());
        assertEquals("Kubernetes", roadmaps.get(0).skillOrFamily());
        assertTrue(roadmaps.get(0).whyItMatters().contains("Aparece de forma repetida"));
    }

    @Test
    void prioritizesRepeatedCriticalGapOverRepeatedSecondaryGap() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("Kafka"), List.of("Docker"), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("Kafka"), List.of("Docker"), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("Kafka", roadmaps.get(0).skillOrFamily());
    }

    @Test
    void mapsAliasesToFamilies() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("PostgreSQL"), List.of("REST"), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("SQL"), List.of("API REST"), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("SQL/PostgreSQL", roadmaps.get(0).skillOrFamily());
        assertEquals("REST APIs", roadmaps.get(1).skillOrFamily());
    }

    @Test
    void visibleSkillGeneratesEvidenceRoadmap() {
        CandidateProfile profile = profile();
        profile.setDeclaredSkillsText("Java, PostgreSQL");

        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile,
                List.of(),
                List.of(
                        result(List.of("PostgreSQL"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("SQL"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("SQL/PostgreSQL", roadmaps.get(0).skillOrFamily());
        assertEquals("Evidenciar mejor SQL/PostgreSQL", roadmaps.get(0).title());
        assertEquals("Refuerzo de evidencia", roadmaps.get(0).toneLabel());
    }

    @Test
    void absentSkillGeneratesLearningRoadmap() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("Docker", roadmaps.get(0).skillOrFamily());
        assertEquals("Practicar Docker", roadmaps.get(0).title());
        assertFalse(roadmaps.get(0).toneLabel().equals("Evidencia visible"));
    }

    @Test
    void projectEvidenceCountsAsVisibleEvidence() {
        CandidateProfileProject project = new CandidateProfileProject();
        project.setTitle("API deploy");
        project.setDescription("Backend deploy practice");
        project.setSkillsText("Docker, Compose");

        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(project),
                List.of(
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("Refuerzo de evidencia", roadmaps.get(0).toneLabel());
    }

    @Test
    void seniorProfileWithSqlGapGetsEvidenceOrDeepeningRoadmap() {
        CandidateProfile profile = profile();
        profile.setTargetSeniority("SENIOR");

        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile,
                List.of(),
                List.of(
                        result(List.of("PostgreSQL"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("SQL"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("SQL/PostgreSQL", roadmaps.get(0).skillOrFamily());
        assertEquals("Profundizacion", roadmaps.get(0).toneLabel());
        assertFalse(roadmaps.get(0).initialSteps().get(0).contains("SELECT"));
        assertTrue(roadmaps.get(0).initialSteps().get(0).contains("modelado"));
    }

    @Test
    void textualSeniorityAvoidsBasicRoadmap() {
        CandidateProfile profile = profile();
        profile.setCvText("Senior backend engineer con experiencia en APIs y arquitectura.");

        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile,
                List.of(),
                List.of(
                        result(List.of("REST"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("API REST"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("REST APIs", roadmaps.get(0).skillOrFamily());
        assertFalse(roadmaps.get(0).initialSteps().get(0).contains("Disenar recursos"));
        assertTrue(roadmaps.get(0).initialSteps().get(0).contains("criterios"));
    }

    @Test
    void backendCloudProfileDoesNotPrioritizeSupportWhenTechnicalGapsExist() {
        CandidateProfile profile = profile();
        profile.setTargetRole("CLOUD");

        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile,
                List.of(),
                List.of(
                        result(List.of("logs", "Docker", "Kubernetes", "Cloud"), List.of(), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("logs", "Docker", "Kubernetes", "Cloud"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals(3, roadmaps.size());
        assertFalse(roadmaps.stream().anyMatch(roadmap -> "Soporte/App Support".equals(roadmap.skillOrFamily())));
    }

    @Test
    void transferableDoesNotCreateRoadmapWithoutRepeatedGap() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(result(
                        List.of(),
                        List.of(),
                        List.of(new TransferableSkill("Docker", "Kubernetes", TransferStrength.PARTIAL, "Relacionado")),
                        List.of(),
                        List.of("Profundizar Kubernetes"),
                        List.of()
                ))
        );

        assertTrue(roadmaps.isEmpty());
    }

    @Test
    void includesTransferAndEquivalenceSignalsAsContextOnly() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(
                                List.of("Kubernetes"),
                                List.of(),
                                List.of(new TransferableSkill("Docker", "Kubernetes", TransferStrength.PARTIAL, "Relacionado")),
                                List.of(new SkillEquivalenceSignal("PostgreSQL", "SQL", "PARTIAL_EQUIVALENCE", "Relacionado")),
                                List.of("Profundizar Kubernetes"),
                                List.of()
                        ),
                        result(List.of("Kubernetes"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals("Kubernetes", roadmaps.get(0).skillOrFamily());
        assertTrue(roadmaps.get(0).relatedSignals().stream().anyMatch(signal -> signal.contains("Transferencia")));
    }

    @Test
    void ignoresFamiliesAlreadyMatched() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of("Docker")),
                        result(List.of("Docker"), List.of(), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertTrue(roadmaps.isEmpty());
    }

    @Test
    void limitsToThreeRoadmaps() {
        List<ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion> roadmaps = service.suggest(
                profile(),
                List.of(),
                List.of(
                        result(List.of("Docker", "Kubernetes", "Kafka", "Cloud"), List.of("Testing"), List.of(), List.of(), List.of(), List.of()),
                        result(List.of("Docker", "Kubernetes", "Kafka", "Cloud"), List.of("Testing"), List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals(3, roadmaps.size());
    }

    private static CandidateProfile profile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java backend profile");
        profile.setTargetRole("BACKEND");
        profile.setTargetSeniority("JUNIOR");
        profile.setSearchMode("FOCUSED");
        return profile;
    }

    private static VectorFirstCompatibilityResult result(
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<TransferableSkill> transferableSkills,
            List<SkillEquivalenceSignal> skillEquivalenceSignals,
            List<String> roadmapSuggestions,
            List<String> matchedSkills
    ) {
        return new VectorFirstCompatibilityResult(
                7L,
                "Backend Engineer",
                "Example",
                1,
                0.7d,
                1,
                "BACKEND",
                "JUNIOR",
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                transferableSkills,
                roadmapSuggestions,
                "Oferta cercana.",
                CompatibilityConfidence.MEDIUM,
                CompatibilityBucket.GOOD_WITH_MINOR_GAPS,
                1,
                0,
                List.of(),
                List.of(),
                List.of(),
                skillEquivalenceSignals
        );
    }
}
