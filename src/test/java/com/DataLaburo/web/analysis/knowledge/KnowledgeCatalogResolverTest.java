package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.TransferStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalDomain;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceSource;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeCatalogResolverTest {
    private KnowledgeCatalogResolver resolver;

    @BeforeEach
    void setUp() {
        KnowledgeCatalogLoader loader = new KnowledgeCatalogLoader(new KnowledgeCatalogValidator());
        resolver = new KnowledgeCatalogResolver(loader.load(
                new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH)
        ));
    }

    @Test
    void resolvesJavaSpringPostgresqlWithProjectEvidenceWithoutCallingItWorkExperience() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "java backend",
                "JUNIOR",
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of(),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Spring Boot", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("PostgreSQL", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.ContextLevel.SUPPORTED, enrichment.contextLevel());
        assertEquals("BACKEND", enrichment.roleFamily().id());
        assertEquals(List.of("JAVA", "SPRING_BOOT", "SQL_POSTGRESQL"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().allMatch(strength ->
                strength.evidenceType() == ProfessionalEvidenceType.PROJECT
                        && strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING));
        assertTrue(enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::explanation)
                .anyMatch(copy -> copy.contains("no experiencia laboral")));
        assertEquals("JUNIOR", enrichment.seniorityGuidance().seniority());
        assertFalse(enrichment.seniorityGuidance().requiresDomainWorkEvidence());
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void resolvesItSupportToBackendJavaAsPartialTransferOnly() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "technical support",
                "BACKEND",
                "SENIOR",
                List.of("Git"),
                List.of("Java", "Spring Boot"),
                List.of("REST"),
                List.of(evidence(
                        "IT Support",
                        ProfessionalEvidenceType.WORK_EXPERIENCE,
                        ProfessionalDomain.SUPPORT
                )),
                false
        ));

        assertEquals(1, enrichment.transfers().size());
        OpportunityKnowledgeEnrichment.Transfer transfer = enrichment.transfers().get(0);
        assertEquals("IT_SUPPORT_TO_BACKEND_JAVA", transfer.id());
        assertEquals(TransferStrength.PARTIAL, transfer.strength());
        assertTrue(transfer.targetTechnologies().containsAll(List.of("Java", "Spring Boot", "REST APIs")));
        assertTrue(transfer.warning().contains("no equivale a experiencia laboral"));
        assertEquals(List.of("JAVA", "SPRING_BOOT", "REST_APIS"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                strength.technologyId().equals("JAVA") || strength.technologyId().equals("SPRING_BOOT")));
        assertTrue(enrichment.seniorityGuidance().requiresDomainWorkEvidence());
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void resolvesDataBiWithSqlAndPowerBiEvidence() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "business intelligence",
                "JUNIOR",
                List.of("SQL", "Power BI"),
                List.of(),
                List.of(),
                List.of(
                        evidence("SQL", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA),
                        evidence("Power BI", ProfessionalEvidenceType.ACADEMIC, ProfessionalDomain.DATA)
                ),
                false
        ));

        assertEquals("DATA", enrichment.roleFamily().id());
        assertEquals(List.of("SQL_POSTGRESQL", "POWER_BI"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().allMatch(strength ->
                strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING));
        assertTrue(enrichment.transfers().isEmpty());
    }

    @Test
    void declaredSkillRemainsWeakAndProducesEvidenceAction() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "BACKEND",
                "JUNIOR",
                List.of("Docker"),
                List.of(),
                List.of(),
                List.of(evidence(
                        "Docker",
                        ProfessionalEvidenceType.DECLARED_ONLY,
                        ProfessionalDomain.CLOUD
                )),
                false
        ));

        OpportunityKnowledgeEnrichment.Strength docker = enrichment.strengths().get(0);
        assertEquals(ProfessionalEvidenceType.DECLARED_ONLY, docker.evidenceType());
        assertEquals(OpportunityKnowledgeEnrichment.EvidenceAssessment.WEAK, docker.evidenceAssessment());
        assertTrue(docker.explanation().contains("sin Dockerfile"));
        assertEquals("EVIDENCE_DOCKER", enrichment.actions().get(0).id());
        assertTrue(enrichment.actions().get(0).reason().contains("sin evidencia directa"));
    }

    @Test
    void insufficientOpportunityMetadataProducesLimitedContextWithoutInventingContent() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "IT_SUPPORT",
                "UNKNOWN",
                "UNKNOWN",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        ));

        assertEquals(OpportunityKnowledgeEnrichment.ContextLevel.LIMITED, enrichment.contextLevel());
        assertNull(enrichment.roleFamily());
        assertTrue(enrichment.strengths().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
        assertTrue(enrichment.roleExplanation().contains("no se infieren requisitos"));
        assertTrue(enrichment.warnings().stream().anyMatch(copy -> copy.contains("poco detalle")));
    }

    @Test
    void skillResolutionIsExactAndDoesNotFuzzyMatchJavaScriptToJava() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "BACKEND",
                "JUNIOR",
                List.of("JavaScript"),
                List.of(),
                List.of(),
                List.of(),
                false
        ));

        assertTrue(enrichment.strengths().isEmpty());
        assertEquals(List.of("JavaScript"), enrichment.unresolvedSignals());
    }

    @Test
    void outputContractHasNoScoreRankFilterOrAptitudeFields() {
        List<String> componentNames = Arrays.stream(OpportunityKnowledgeEnrichment.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList();

        assertTrue(componentNames.stream().noneMatch(name ->
                name.contains("score")
                        || name.contains("rank")
                        || name.contains("filter")
                        || name.contains("apt")));
    }

    private static ProfessionalSkillEvidence evidence(
            String skill,
            ProfessionalEvidenceType type,
            ProfessionalDomain domain
    ) {
        ProfessionalEvidenceStrength strength = switch (type) {
            case WORK_EXPERIENCE -> ProfessionalEvidenceStrength.STRONG;
            case PROJECT, ACADEMIC -> ProfessionalEvidenceStrength.MEDIUM;
            case DECLARED_ONLY, TRANSFERABLE -> ProfessionalEvidenceStrength.WEAK;
            case MISSING -> ProfessionalEvidenceStrength.NONE;
        };
        ProfessionalEvidenceSource source = switch (type) {
            case PROJECT -> ProfessionalEvidenceSource.PROJECT;
            case DECLARED_ONLY -> ProfessionalEvidenceSource.DECLARED_SKILLS;
            case TRANSFERABLE -> ProfessionalEvidenceSource.TRANSFER_RULE;
            default -> ProfessionalEvidenceSource.CV_TEXT;
        };
        return new ProfessionalSkillEvidence(
                skill,
                type,
                strength,
                domain,
                source,
                "test",
                "test evidence",
                List.of()
        );
    }
}
