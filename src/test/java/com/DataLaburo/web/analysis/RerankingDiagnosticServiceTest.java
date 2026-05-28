package com.DataLaburo.web.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RerankingDiagnosticServiceTest {
    private final RerankingDiagnosticService service = new RerankingDiagnosticService();

    @Test
    void suggestedRanksCanChangeWithoutChangingVectorOrder() {
        VectorFirstCompatibilityResult iam = withDiagnostic(
                result(1, "IAM Engineer", "IAM", "SENIOR", List.of("SQL", "REST", "Git"), List.of(), List.of(), List.of()),
                "JUNIOR"
        );
        VectorFirstCompatibilityResult backend = withDiagnostic(
                result(2, "Backend Engineer", "BACKEND", "JUNIOR", List.of("Java", "REST"), List.of(), List.of(), List.of()),
                "JUNIOR"
        );

        List<VectorFirstCompatibilityResult> diagnostics = service.assignSuggestedRanks(List.of(iam, backend));

        assertEquals(1, diagnostics.get(0).vectorRank());
        assertEquals(2, diagnostics.get(1).vectorRank());
        assertEquals(2, diagnostics.get(0).suggestedRerankRank());
        assertEquals(-1, diagnostics.get(0).suggestedRankDelta());
        assertEquals(1, diagnostics.get(1).suggestedRerankRank());
        assertEquals(1, diagnostics.get(1).suggestedRankDelta());
        assertFalse(diagnostics.get(0).rerankReasons().isEmpty());
    }

    @Test
    void genericSkillsOnlyCannotEnterReadyOrGoodBuckets() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(1, "IAM Engineer", "IAM", "SENIOR", List.of("SQL", "REST", "Git"), List.of(), List.of(), List.of()),
                "JUNIOR"
        );

        assertNotEquals(CompatibilityBucket.READY_NOW, result.compatibilityBucket());
        assertNotEquals(CompatibilityBucket.GOOD_WITH_MINOR_GAPS, result.compatibilityBucket());
        assertEquals(CompatibilityBucket.LOW_FIT, result.compatibilityBucket());
    }

    @Test
    void strongTransferableOpportunityIsTransferableNotReadyNow() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Cloud Support",
                        "CLOUD",
                        "JUNIOR",
                        List.of("Java"),
                        List.of("Kubernetes"),
                        List.of(),
                        List.of(new TransferableSkill("Docker", "Kubernetes", TransferStrength.STRONG, "base transferible"))
                ),
                "JUNIOR"
        );

        assertEquals(CompatibilityBucket.TRANSFERABLE, result.compatibilityBucket());
    }

    @Test
    void noMatchedSkillsFallsToLowFit() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(1, "Technical Support Jr", "IT_SUPPORT", "JUNIOR", List.of(), List.of(), List.of("Windows"), List.of()),
                "JUNIOR"
        );

        assertEquals(CompatibilityBucket.LOW_FIT, result.compatibilityBucket());
    }

    @Test
    void seniorRoleDropsAgainstJuniorProfile() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(1, "Database Developer Senior", "DATABASE", "SENIOR", List.of("PostgreSQL"), List.of(), List.of("Oracle"), List.of()),
                "JUNIOR"
        );

        assertEquals(CompatibilityBucket.ASPIRATIONAL, result.compatibilityBucket());
        assertTrue(result.rerankSignals().stream().anyMatch(signal -> "SENIORITY_TOO_HIGH".equals(signal.name())));
        assertTrue(result.rerankReasons().stream().anyMatch(reason -> reason.contains("seniority superior")));
    }

    @Test
    void weakMatchWithGenericSkillsDoesNotMoveUp() {
        VectorFirstCompatibilityResult lowFit = withManualDiagnostic(
                result(1, "IAM Engineer", "IAM", "SENIOR", List.of(), List.of(), List.of(), List.of()),
                CompatibilityBucket.LOW_FIT
        );
        VectorFirstCompatibilityResult weakGeneric = withManualDiagnostic(
                result(2, "Analista Programador", "DOTNET_FULLSTACK", "MID", List.of("SQL"), List.of(), List.of("C#"), List.of()),
                CompatibilityBucket.WEAK_MATCH
        );
        VectorFirstCompatibilityResult anotherLowFit = withManualDiagnostic(
                result(3, "Application Support Senior", "APP_SUPPORT", "SENIOR", List.of(), List.of(), List.of(), List.of()),
                CompatibilityBucket.LOW_FIT
        );

        List<VectorFirstCompatibilityResult> diagnostics = service.assignSuggestedRanks(List.of(lowFit, weakGeneric, anotherLowFit));

        assertEquals(2, diagnostics.get(1).suggestedRerankRank());
        assertEquals(0, diagnostics.get(1).suggestedRankDelta());
    }

    @Test
    void weakMatchWithoutGenericOnlyCanMoveUpAtMostTwoPositions() {
        VectorFirstCompatibilityResult low1 = withManualDiagnostic(result(1, "Low 1", "IAM", "SENIOR", List.of(), List.of(), List.of(), List.of()), CompatibilityBucket.LOW_FIT);
        VectorFirstCompatibilityResult low2 = withManualDiagnostic(result(2, "Low 2", "IAM", "SENIOR", List.of(), List.of(), List.of(), List.of()), CompatibilityBucket.LOW_FIT);
        VectorFirstCompatibilityResult low3 = withManualDiagnostic(result(3, "Low 3", "IAM", "SENIOR", List.of(), List.of(), List.of(), List.of()), CompatibilityBucket.LOW_FIT);
        VectorFirstCompatibilityResult weakSpecific = withManualDiagnostic(
                result(4, "Weak Java", "CLOUD", "MID", List.of("Java"), List.of(), List.of(), List.of()),
                CompatibilityBucket.WEAK_MATCH
        );

        List<VectorFirstCompatibilityResult> diagnostics = service.assignSuggestedRanks(List.of(low1, low2, low3, weakSpecific));

        assertEquals(2, diagnostics.get(3).suggestedRerankRank());
        assertEquals(2, diagnostics.get(3).suggestedRankDelta());
    }

    @Test
    void weakMatchWithRoleWarningDoesNotMoveUp() {
        VectorFirstCompatibilityResult lowFit = withManualDiagnostic(
                result(1, "Low 1", "IAM", "SENIOR", List.of(), List.of(), List.of(), List.of()),
                CompatibilityBucket.LOW_FIT
        );
        VectorFirstCompatibilityResult weakUnknown = withManualDiagnostic(
                result(2, "Desarrollador de Android", "UNKNOWN", "MID", List.of("REST"), List.of(), List.of(), List.of()),
                CompatibilityBucket.WEAK_MATCH,
                List.of("Rol no determinado; no conviene usar reranking para tapar este caso.")
        );

        List<VectorFirstCompatibilityResult> diagnostics = service.assignSuggestedRanks(List.of(lowFit, weakUnknown));

        assertEquals(2, diagnostics.get(1).suggestedRerankRank());
        assertEquals(0, diagnostics.get(1).suggestedRankDelta());
    }

    @Test
    void transferableAndAspirationalCanMoveAboveWeakMatch() {
        VectorFirstCompatibilityResult weak = withManualDiagnostic(
                result(1, "Weak SQL", "DOTNET_FULLSTACK", "MID", List.of("SQL"), List.of(), List.of(), List.of()),
                CompatibilityBucket.WEAK_MATCH
        );
        VectorFirstCompatibilityResult transferable = withManualDiagnostic(
                result(2, "Transferable Cloud", "CLOUD", "MID", List.of("Java"), List.of(), List.of(), List.of()),
                CompatibilityBucket.TRANSFERABLE
        );
        VectorFirstCompatibilityResult aspirational = withManualDiagnostic(
                result(3, "Senior Database", "DATABASE", "SENIOR", List.of("PostgreSQL"), List.of(), List.of(), List.of()),
                CompatibilityBucket.ASPIRATIONAL
        );

        List<VectorFirstCompatibilityResult> diagnostics = service.assignSuggestedRanks(List.of(weak, transferable, aspirational));

        assertEquals(3, diagnostics.get(0).suggestedRerankRank());
        assertEquals(1, diagnostics.get(1).suggestedRerankRank());
        assertEquals(2, diagnostics.get(2).suggestedRerankRank());
    }

    @Test
    void supportProfileCanAlignWithApplicationSupportRole() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(1, "Application Support Jr", "APP_SUPPORT", "JUNIOR", List.of("Windows Server", "ITIL"), List.of(), List.of("JBoss"), List.of()),
                "JUNIOR",
                "IT_SUPPORT"
        );

        assertEquals(CompatibilityBucket.GOOD_WITH_MINOR_GAPS, result.compatibilityBucket());
        assertTrue(result.rerankSignals().stream().anyMatch(signal -> "ROLE_ALIGNED".equals(signal.name())));
    }

    @Test
    void backendTraineeWithProjectsAndSpecificMatchesIsNotLowFitForAlignedBackendRole() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Software Engineer Backend",
                        "BACKEND",
                        "SENIOR",
                        List.of("Java", "REST"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT
                ),
                "TRAINEE",
                "BACKEND"
        );

        assertEquals(CompatibilityBucket.ASPIRATIONAL, result.compatibilityBucket());
        assertTrue(result.rerankReasons().stream().anyMatch(reason -> reason.contains("seniority superior")));
    }

    @Test
    void backendTraineeStillLowFitForIamSeniorWithGenericMatches() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "IAM Engineer",
                        "IAM",
                        "SENIOR",
                        List.of("SQL", "REST", "Git"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT
                ),
                "TRAINEE",
                "BACKEND"
        );

        assertEquals(CompatibilityBucket.LOW_FIT, result.compatibilityBucket());
    }

    @Test
    void backendTraineeStillLowFitForSupportRoleWithoutMatchedSkills() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Technical Support Jr",
                        "IT_SUPPORT",
                        "JUNIOR",
                        List.of(),
                        List.of(),
                        List.of("Windows Server"),
                        List.of(),
                        EvidenceLevel.PROJECT
                ),
                "TRAINEE",
                "BACKEND"
        );

        assertEquals(CompatibilityBucket.LOW_FIT, result.compatibilityBucket());
    }

    @Test
    void alignedRoleWithoutEvidenceDoesNotRiseLikeProjectEvidence() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Backend Engineer",
                        "BACKEND",
                        "JUNIOR",
                        List.of("Java", "REST"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.MENTIONED_ONLY
                ),
                "TRAINEE",
                "BACKEND"
        );

        assertEquals(CompatibilityBucket.WEAK_MATCH, result.compatibilityBucket());
    }

    @Test
    void seniorBackendProfileCanBeReadyNowForAlignedSeniorBackendRole() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Senior Backend Platform Engineer",
                        "BACKEND",
                        "SENIOR",
                        List.of("Java", "REST", "Spring Boot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.WORK_EXPERIENCE
                ),
                "SENIOR",
                "BACKEND"
        );

        assertEquals(CompatibilityBucket.READY_NOW, result.compatibilityBucket());
    }

    @Test
    void dataProfileCanAlignWithDataRoleWhenSignalsAreSpecific() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "BI Data Analyst",
                        "DATA",
                        "JUNIOR",
                        List.of("SQL", "Power BI"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT
                ),
                "JUNIOR",
                "DATA"
        );

        assertEquals(CompatibilityBucket.READY_NOW, result.compatibilityBucket());
    }

    @Test
    void meliBackendForBackendTraineeDoesNotProduceProfileQaMisalignmentReason() {
        VectorFirstCompatibilityResult result = withDiagnostic(
                result(
                        1,
                        "Software Engineer Backend",
                        "BACKEND",
                        "MID",
                        List.of("Java", "REST"),
                        List.of(),
                        List.of(),
                        List.of(),
                        EvidenceLevel.PROJECT
                ),
                "TRAINEE",
                "BACKEND"
        );

        assertNotEquals(CompatibilityBucket.LOW_FIT, result.compatibilityBucket());
        assertTrue(result.rerankReasons().stream().noneMatch(reason -> reason.contains("perfil QA")));
        assertNotNull(result.rerankSignals().stream()
                .filter(signal -> "ROLE_ALIGNED".equals(signal.name()))
                .findFirst()
                .orElse(null));
    }

    private VectorFirstCompatibilityResult withDiagnostic(VectorFirstCompatibilityResult result, String profileSeniority) {
        return withDiagnostic(result, profileSeniority, null);
    }

    private VectorFirstCompatibilityResult withDiagnostic(
            VectorFirstCompatibilityResult result,
            String profileSeniority,
            String profileRole
    ) {
        CompatibilitySignalContext context = new CompatibilitySignalContext(
                result.detectedRole(),
                result.detectedSeniority(),
                profileSeniority,
                profileRole
        );
        return result.withDiagnostic(service.evaluate(result, context));
    }

    private static VectorFirstCompatibilityResult withManualDiagnostic(
            VectorFirstCompatibilityResult result,
            CompatibilityBucket bucket
    ) {
        return withManualDiagnostic(result, bucket, List.of());
    }

    private static VectorFirstCompatibilityResult withManualDiagnostic(
            VectorFirstCompatibilityResult result,
            CompatibilityBucket bucket,
            List<String> warnings
    ) {
        return result.withDiagnostic(new RerankingDiagnostic(
                bucket,
                List.of("Bucket diagnostico asignado: " + bucket + "."),
                warnings,
                List.of()
        ));
    }

    private static VectorFirstCompatibilityResult result(
            int vectorRank,
            String title,
            String role,
            String seniority,
            List<String> matchedSkills,
            List<String> criticalGaps,
            List<String> secondaryGaps,
            List<TransferableSkill> transferableSkills
    ) {
        return result(
                vectorRank,
                title,
                role,
                seniority,
                matchedSkills,
                criticalGaps,
                secondaryGaps,
                transferableSkills,
                EvidenceLevel.PROJECT
        );
    }

    private static VectorFirstCompatibilityResult result(
            int vectorRank,
            String title,
            String role,
            String seniority,
            List<String> matchedSkills,
            List<String> criticalGaps,
            List<String> secondaryGaps,
            List<TransferableSkill> transferableSkills,
            EvidenceLevel evidenceLevel
    ) {
        return new VectorFirstCompatibilityResult(
                (long) vectorRank,
                title,
                "Example",
                vectorRank,
                0.65d,
                vectorRank,
                role,
                seniority,
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                evidenceLevel,
                matchedSkills,
                criticalGaps,
                secondaryGaps,
                transferableSkills,
                List.of(),
                "diagnostic fixture",
                CompatibilityConfidence.MEDIUM
        );
    }
}
