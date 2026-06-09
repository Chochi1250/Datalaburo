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

class ProfileImprovementSuggestionServiceTest {
    private final ProfileImprovementSuggestionService service = new ProfileImprovementSuggestionService();

    @Test
    void criticalGapAlreadyVisibleSuggestsEvidenceInsteadOfLearning() {
        CandidateProfile profile = completeProfile();
        profile.setDeclaredSkillsText("Java, Kubernetes");

        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(profile, List.of(), result(
                        List.of("Kubernetes"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertEquals("EVIDENCE", suggestions.get(0).category());
        assertTrue(suggestions.get(0).message().contains("Si ya lo usaste"));
        assertFalse(suggestions.stream().anyMatch(suggestion -> "LEARNING_GAP".equals(suggestion.category())));
    }

    @Test
    void criticalGapVisibleInProjectSuggestsEvidenceInsteadOfLearning() {
        CandidateProfileProject project = new CandidateProfileProject();
        project.setTitle("Deploy API");
        project.setDescription("Deployment practice.");
        project.setSkillsText("Docker, Kubernetes");

        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(completeProfile(), List.of(project), result(
                        List.of("Kubernetes"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertEquals("EVIDENCE", suggestions.get(0).category());
        assertFalse(suggestions.stream().anyMatch(suggestion -> "LEARNING_GAP".equals(suggestion.category())));
    }

    @Test
    void absentCriticalGapSuggestsConditionalLearningGap() {
        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(completeProfile(), List.of(), result(
                        List.of("Kubernetes"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertEquals("LEARNING_GAP", suggestions.get(0).category());
        assertTrue(suggestions.get(0).message().contains("Podrías reforzar Kubernetes"));
    }

    @Test
    void transferableSkillSuggestsTransferWithoutTreatingItAsPresent() {
        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(completeProfile(), List.of(), result(
                        List.of(),
                        List.of(),
                        List.of(new TransferableSkill(
                                "Docker",
                                "Kubernetes",
                                TransferStrength.PARTIAL,
                                "Base de contenedores transferible"
                        )),
                        List.of(),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertEquals("TRANSFER", suggestions.get(0).category());
        assertEquals("Si corresponde, podrías explicar cómo tu experiencia en Docker se relaciona con Kubernetes.",
                suggestions.get(0).message());
    }

    @Test
    void partialRelationSuggestsClarifyingContext() {
        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(completeProfile(), List.of(), result(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new SkillEquivalenceSignal(
                                "PostgreSQL",
                                "SQL",
                                "PARTIAL_EQUIVALENCE",
                                "Base relacional relacionada"
                        )),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertEquals("PARTIAL_RELATION", suggestions.get(0).category());
        assertTrue(suggestions.get(0).message().contains("PostgreSQL"));
        assertTrue(suggestions.get(0).message().contains("SQL"));
    }

    @Test
    void lowEvidenceOrConfidenceSuggestsConcreteEvidence() {
        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(completeProfile(), List.of(), result(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.MENTIONED_ONLY,
                        CompatibilityConfidence.MEDIUM
                ));

        assertEquals("EVIDENCE", suggestions.get(0).category());
        assertTrue(suggestions.get(0).message().contains("evidencia concreta"));
    }

    @Test
    void incompleteMetadataSuggestsCompletingVisibleProfile() {
        CandidateProfile profile = completeProfile();
        profile.setHeadline(null);
        profile.setSummary(null);
        profile.setDeclaredSkillsText(null);
        profile.setGithubUrl(null);

        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggestProfile(profile);

        assertEquals("PROFILE_METADATA", suggestions.get(0).category());
        assertTrue(suggestions.get(0).message().contains("presentación visible"));
    }

    @Test
    void profileMetadataSuggestionsAreNotRepeatedPerOffer() {
        CandidateProfile profile = completeProfile();
        profile.setHeadline(null);
        profile.setTargetRole("UNDECIDED");

        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(profile, List.of(), result(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT,
                        CompatibilityConfidence.HIGH
                ));

        assertTrue(suggestions.isEmpty());
        assertFalse(service.suggestProfile(profile).isEmpty());
    }

    @Test
    void limitsSuggestionsToThreePerResult() {
        CandidateProfile profile = completeProfile();
        profile.setTargetRole("UNDECIDED");

        List<ProfileImprovementSuggestionService.ProfileImprovementSuggestion> suggestions =
                service.suggest(profile, List.of(), result(
                        List.of("Kubernetes", "Kafka"),
                        List.of("AWS", "Terraform"),
                        List.of(new TransferableSkill(
                                "Docker",
                                "Kubernetes",
                                TransferStrength.PARTIAL,
                                "Base de contenedores transferible"
                        )),
                        List.of(new SkillEquivalenceSignal(
                                "PostgreSQL",
                                "SQL",
                                "PARTIAL_EQUIVALENCE",
                                "Base relacional relacionada"
                        )),
                        EvidenceLevel.NO_EVIDENCE,
                        CompatibilityConfidence.LOW
                ));

        assertEquals(3, suggestions.size());
    }

    private static CandidateProfile completeProfile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java backend profile with Spring Boot APIs.");
        profile.setHeadline("Backend Java developer");
        profile.setSummary("Builds backend APIs.");
        profile.setDeclaredSkillsText("Java, Spring Boot, PostgreSQL");
        profile.setGithubUrl("https://github.com/example");
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
            EvidenceLevel evidenceLevel,
            CompatibilityConfidence confidence
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
                evidenceLevel,
                List.of("Java"),
                missingCriticalSkills,
                missingSecondarySkills,
                transferableSkills,
                List.of(),
                "Oferta cercana.",
                confidence,
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
