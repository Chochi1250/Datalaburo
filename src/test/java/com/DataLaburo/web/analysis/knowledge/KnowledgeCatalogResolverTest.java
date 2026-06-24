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
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.DIRECT_COVERAGE, enrichment.coverageLevel());
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
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
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
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.DIRECT_COVERAGE, enrichment.coverageLevel());
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
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.LOW_CONTEXT, enrichment.coverageLevel());
        assertNull(enrichment.roleFamily());
        assertTrue(enrichment.strengths().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
        assertTrue(enrichment.roleExplanation().contains("no se infieren requisitos"));
        assertTrue(enrichment.warnings().stream().anyMatch(copy -> copy.contains("poco detalle")));
    }

    @Test
    void skillResolutionIsExactAndDoesNotResolveJavaScriptAsJava() {
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

        assertEquals(List.of("JAVASCRIPT"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength -> strength.technologyId().equals("JAVA")));
        assertTrue(enrichment.unresolvedSignals().isEmpty());
    }

    @Test
    void profile8ToCloudDevOpsKeepsSupportAndPlatformSignalsAsPartialTransfer() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "IT_SUPPORT",
                "CLOUD_DEVOPS",
                "MID",
                List.of("OpenShift", "Linux", "Docker", "Git"),
                List.of("AWS", "Terraform"),
                List.of(),
                List.of(
                        evidence("IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("OpenShift", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.INFRA),
                        evidence("Linux", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.INFRA),
                        evidence("Docker", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Git", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals(List.of("OPENSHIFT", "LINUX", "DOCKER", "GIT"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertEquals(List.of("AWS", "TERRAFORM"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                strength.technologyId().equals("AWS") || strength.technologyId().equals("TERRAFORM")));
        assertEquals("IT_SUPPORT_TO_CLOUD_DEVOPS", enrichment.transfers().get(0).id());
        assertTrue(enrichment.transfers().get(0).warning().contains("no demuestran AWS"));
        assertTrue(enrichment.seniorityGuidance().requiresDomainWorkEvidence());
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void profile4ToTier3KeepsDirectSupportAsPartialAppSupportTransfer() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "IT_SUPPORT",
                "APP_SUPPORT_OPERATIONS",
                "JUNIOR",
                List.of("ServiceNow", "Jira"),
                List.of("Grafana", "Datadog", "Postman"),
                List.of(),
                List.of(
                        evidence("IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("ServiceNow", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("Jira", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("IT_SUPPORT_TO_APP_SUPPORT_OPERATIONS", enrichment.transfers().get(0).id());
        assertTrue(enrichment.transfers().get(0).warning().contains("no demuestra observabilidad"));
        assertEquals(List.of("GRAFANA", "DATADOG", "POSTMAN"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                List.of("GRAFANA", "DATADOG", "POSTMAN").contains(strength.technologyId())));
    }

    @Test
    void profile7JavaProjectsRemainOutOfScopeForIam() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "WEB_FULL_STACK",
                "IAM",
                "MID",
                List.of("Java"),
                List.of("IAM", "SAML"),
                List.of(),
                List.of(evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE, enrichment.coverageLevel());
        assertEquals("SECURITY_IAM", enrichment.roleFamily().id());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
        assertNull(enrichment.seniorityGuidance());
        assertTrue(enrichment.warnings().stream().anyMatch(copy -> copy.contains("transferencia segura")));
    }

    @Test
    void profile1BackendToFullStackKeepsReactAsExplicitGap() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "full stack",
                "MID",
                List.of("Java", "Spring Boot", "REST"),
                List.of("React"),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Spring Boot", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA),
                        evidence("REST APIs", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("BACKEND_TO_WEB_FULL_STACK", enrichment.transfers().get(0).id());
        assertEquals(List.of("REACT"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength -> strength.technologyId().equals("REACT")));
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void profile5DataSupportKeepsFabricAsSpecificUninferredGap() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "DATA",
                "JUNIOR",
                List.of("SQL", "Power BI"),
                List.of(),
                List.of("Microsoft Fabric"),
                List.of(
                        evidence("SQL", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.DATA),
                        evidence("Power BI", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.DATA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.DIRECT_COVERAGE, enrichment.coverageLevel());
        assertEquals(List.of("SQL_POSTGRESQL", "POWER_BI"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertEquals(List.of("MICROSOFT_FABRIC"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                strength.technologyId().equals("MICROSOFT_FABRIC")));
    }

    @Test
    void profile5ToWeakSlbMetadataStaysLowContextWithoutDerivedContent() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "DATA",
                "SENIOR",
                List.of("SQL"),
                List.of("AWS", "Terraform", "Microsoft Fabric"),
                List.of("Spark"),
                List.of(evidence("SQL", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.DATA)),
                true
        ));

        assertEquals(OpportunityKnowledgeEnrichment.ContextLevel.LIMITED, enrichment.contextLevel());
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.LOW_CONTEXT, enrichment.coverageLevel());
        assertEquals("DATA", enrichment.roleFamily().id());
        assertTrue(enrichment.strengths().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
        assertNull(enrichment.seniorityGuidance());
    }

    @Test
    void activeDirectoryDoesNotResolveAsIam() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "IT_SUPPORT",
                "IT_SUPPORT",
                "JUNIOR",
                List.of("Active Directory"),
                List.of(),
                List.of(),
                List.of(evidence(
                        "Active Directory",
                        ProfessionalEvidenceType.WORK_EXPERIENCE,
                        ProfessionalDomain.SUPPORT
                )),
                false
        ));

        assertEquals(List.of("ACTIVE_DIRECTORY"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength -> strength.technologyId().equals("IAM")));
    }

    @Test
    void oauthAndOidcProjectEvidenceDoesNotBecomeProfessionalIamExperience() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "SECURITY_IAM",
                "MID",
                List.of("OAuth 2.0", "OIDC"),
                List.of(),
                List.of(),
                List.of(
                        evidence("OAuth 2.0", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("OIDC", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE, enrichment.coverageLevel());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.strengths().isEmpty());
        assertNull(enrichment.seniorityGuidance());
    }

    @Test
    void dockerDoesNotResolveAsKubernetes() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "CLOUD_DEVOPS",
                "CLOUD_DEVOPS",
                "JUNIOR",
                List.of("Docker"),
                List.of(),
                List.of(),
                List.of(evidence("Docker", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.CLOUD)),
                false
        ));

        assertEquals(List.of("DOCKER"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength -> strength.technologyId().equals("KUBERNETES")));
    }

    @Test
    void backendJavaToFrontendReactIsPartialAndTransfersOnlyRest() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "WEB_FRONTEND",
                "MID",
                List.of("REST"),
                List.of("React"),
                List.of("Accessibility"),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA),
                        evidence("REST APIs", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("BACKEND_TO_WEB_FRONTEND", enrichment.transfers().get(0).id());
        assertEquals(List.of("REST APIs"), enrichment.transfers().get(0).sourceTechnologies());
        assertEquals(List.of("REST_APIS"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertEquals(List.of("REACT", "ACCESSIBILITY"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.transfers().get(0).warning().contains("no demuestran React"));
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void dataBiToDataEngineeringKeepsAirflowDbtAndSparkUnproven() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "DATA_ENGINEERING",
                "MID",
                List.of("SQL", "Power BI"),
                List.of("Airflow", "dbt", "Spark"),
                List.of(),
                List.of(
                        evidence("SQL", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.DATA),
                        evidence("Power BI", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.DATA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("DATA_TO_DATA_ENGINEERING", enrichment.transfers().get(0).id());
        assertEquals(List.of("SQL / PostgreSQL", "Power BI"), enrichment.transfers().get(0).sourceTechnologies());
        assertEquals(List.of("SQL_POSTGRESQL", "POWER_BI"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertEquals(List.of("AIRFLOW", "DBT", "APACHE_SPARK"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void itSupportToInfrastructureRequiresOwnEvidenceForAdvancedNetworking() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "IT_SUPPORT",
                "INFRASTRUCTURE_NETWORKS",
                "MID",
                List.of("Active Directory", "DNS", "Windows Server", "Linux"),
                List.of("Routing", "Firewalls", "VMware"),
                List.of(),
                List.of(
                        evidence("IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("Active Directory", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("DNS", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("Windows Server", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT),
                        evidence("Linux", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("IT_SUPPORT_TO_INFRASTRUCTURE_NETWORKS", enrichment.transfers().get(0).id());
        assertEquals(List.of("ROUTING", "FIREWALLS", "VMWARE"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.transfers().get(0).warning().contains("no demuestran networking avanzado"));
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void applicationSqlToDatabaseEngineeringDoesNotBecomeDbaEvidence() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "DATABASE_ENGINEERING",
                "MID",
                List.of("SQL"),
                List.of("Backups", "Query Tuning", "High Availability", "Replication"),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("SQL", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("BACKEND_TO_DATABASE_ENGINEERING", enrichment.transfers().get(0).id());
        OpportunityKnowledgeEnrichment.Strength sql = enrichment.strengths().get(0);
        assertEquals("SQL_POSTGRESQL", sql.technologyId());
        assertEquals(OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING, sql.evidenceAssessment());
        assertEquals(List.of("BACKUPS", "QUERY_TUNING", "HIGH_AVAILABILITY", "REPLICATION"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                strength.technologyId().equals("DATABASE_ADMINISTRATION")));
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void backendProjectTestsToQaAutomationRemainSupportingOnly() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "QA_AUTOMATION",
                "MID",
                List.of("JUnit", "Postman"),
                List.of("Selenium", "Test Cases"),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("JUnit", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Postman", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("BACKEND_TO_QA_AUTOMATION", enrichment.transfers().get(0).id());
        assertTrue(enrichment.strengths().stream().allMatch(strength ->
                strength.evidenceType() == ProfessionalEvidenceType.PROJECT
                        && strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING));
        assertEquals(List.of("SELENIUM", "TEST_CASES"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void backendOrCloudGenericSignalsDoNotRescueSecurityEngineering() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "SECURITY_ENGINEERING",
                "MID",
                List.of("Docker", "Linux", "REST"),
                List.of("OWASP", "SAST"),
                List.of(),
                List.of(
                        evidence("Docker", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Linux", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA),
                        evidence("REST APIs", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE, enrichment.coverageLevel());
        assertTrue(enrichment.strengths().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.transfers().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
        assertNull(enrichment.seniorityGuidance());
    }

    @Test
    void pythonAndSimpleLlmIntegrationAreOnlyPartialAiTransfer() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "AI_ML_APPLIED",
                "MID",
                List.of("Python", "LLM"),
                List.of("RAG", "MLOps", "Model Evaluation"),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Python", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("LLM", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals("BACKEND_TO_AI_ML_APPLIED", enrichment.transfers().get(0).id());
        assertEquals(List.of("Python", "Large Language Models"), enrichment.transfers().get(0).sourceTechnologies());
        assertEquals(List.of("RAG", "MLOPS", "MODEL_EVALUATION"), enrichment.gaps().stream()
                .map(OpportunityKnowledgeEnrichment.Gap::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().allMatch(strength ->
                strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING));
        assertFalse(enrichment.seniorityGuidance().domainWorkEvidencePresent());
    }

    @Test
    void documentedRagEmbeddingsAndVectorDatabaseRemainProjectEvidence() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "AI_ML_APPLIED",
                "JUNIOR",
                List.of("Python", "LLM", "RAG", "Embeddings", "Vector Database"),
                List.of(),
                List.of(),
                List.of(
                        evidence("Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Python", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("LLM", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA),
                        evidence("RAG", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA),
                        evidence("Embeddings", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA),
                        evidence("Vector Database", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA)
                ),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE, enrichment.coverageLevel());
        assertEquals(List.of("PYTHON", "LLM", "RAG", "EMBEDDINGS", "VECTOR_DATABASES"), enrichment.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(enrichment.strengths().stream().allMatch(strength ->
                strength.evidenceType() == ProfessionalEvidenceType.PROJECT
                        && strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING));
        assertTrue(enrichment.strengths().stream().noneMatch(strength ->
                strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.STRONG));
    }

    @Test
    void powerBiDoesNotResolveAsSparkAndPythonAloneDoesNotCreateAiCoverage() {
        OpportunityKnowledgeEnrichment data = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "DATA",
                "JUNIOR",
                List.of("Power BI"),
                List.of(),
                List.of(),
                List.of(evidence("Power BI", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA)),
                false
        ));
        OpportunityKnowledgeEnrichment ai = resolver.resolve(new KnowledgeResolutionInput(
                "DATA",
                "AI_ML_APPLIED",
                "JUNIOR",
                List.of("Python"),
                List.of(),
                List.of(),
                List.of(evidence("Python", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.DATA)),
                false
        ));

        assertEquals(List.of("POWER_BI"), data.strengths().stream()
                .map(OpportunityKnowledgeEnrichment.Strength::technologyId)
                .toList());
        assertTrue(data.strengths().stream().noneMatch(strength -> strength.technologyId().equals("APACHE_SPARK")));
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE, ai.coverageLevel());
        assertTrue(ai.strengths().isEmpty());
        assertTrue(ai.transfers().isEmpty());
    }

    @Test
    void deliberatelyExcludedRoleStaysOutOfScopeWithoutGenericAffinity() {
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                "BACKEND",
                "mobile developer",
                "JUNIOR",
                List.of("Java"),
                List.of(),
                List.of(),
                List.of(evidence("Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA)),
                false
        ));

        assertEquals(OpportunityKnowledgeEnrichment.ContextLevel.SUPPORTED, enrichment.contextLevel());
        assertEquals(OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE, enrichment.coverageLevel());
        assertNull(enrichment.roleFamily());
        assertTrue(enrichment.strengths().isEmpty());
        assertTrue(enrichment.gaps().isEmpty());
        assertTrue(enrichment.actions().isEmpty());
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
