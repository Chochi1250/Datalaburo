package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.CompatibilityBucket;
import com.DataLaburo.web.analysis.CompatibilityCategory;
import com.DataLaburo.web.analysis.CompatibilityConfidence;
import com.DataLaburo.web.analysis.EvidenceLevel;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.analysis.evidence.ProfessionalDomain;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceSource;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;
import com.DataLaburo.web.analysis.evidence.ProfileEvidenceSummary;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunityKnowledgeDetailMapperTest {
    private OpportunityKnowledgeDetailMapper mapper;

    @BeforeEach
    void setUp() {
        KnowledgeCatalogLoader loader = new KnowledgeCatalogLoader(new KnowledgeCatalogValidator());
        KnowledgeCatalog catalog = loader.load(new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH));
        mapper = new OpportunityKnowledgeDetailMapper(new KnowledgeCatalogResolver(catalog));
    }

    @Test
    void backendTraineeProjectEvidenceNeverBecomesWorkExperience() {
        CandidateProfile profile = profile("BACKEND", "TRAINEE");
        VectorFirstCompatibilityResult result = result(
                "BACKEND",
                "JUNIOR",
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of(),
                List.of()
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.BACKEND_JAVA,
                projectEvidence("Java", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("Spring Boot", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("PostgreSQL", ProfessionalDomain.DATA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job("Backend Developer", substantialDescription()),
                result,
                evidence
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertEquals(List.of("Java", "Spring Boot", "SQL / PostgreSQL"), view.strengths().stream()
                .map(OpportunityKnowledgeDetailView.StrengthItem::skill)
                .toList());
        assertTrue(view.showSupportCards());
        assertTrue(view.strengthsFullWidth());
        assertFalse(view.gapsFullWidth());
        assertTrue(view.strengths().stream().allMatch(item ->
                item.evidenceTypeCode().equals("PROJECT")
                        && item.limit().contains("no como experiencia laboral")));
    }

    @Test
    void cloudDevopsTitleWinsOverIncidentalDatabaseSignals() {
        CandidateProfile profile = profile("IT_SUPPORT", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "DATABASE",
                "MID",
                List.of("OpenShift", "Linux", "Docker", "Git"),
                List.of("AWS", "Terraform"),
                List.of("PostgreSQL", "MongoDB")
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.SUPPORT,
                workEvidence("IT Support", ProfessionalDomain.SUPPORT),
                workEvidence("OpenShift", ProfessionalDomain.INFRA),
                workEvidence("Linux", ProfessionalDomain.INFRA),
                projectEvidence("Docker", ProfessionalDomain.CLOUD),
                projectEvidence("Git", ProfessionalDomain.BACKEND_JAVA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job(
                        "Cloud DevOps Engineer - Latin America - Remote",
                        substantialDescription()
                                + " The stack mentions PostgreSQL, Python and MongoDB as application dependencies."
                ),
                result,
                evidence
        );

        assertEquals("PARTIAL_COVERAGE", view.coverageCode());
        assertEquals("Cloud / DevOps", view.roleFamilyLabel());
        assertEquals(List.of("AWS", "Terraform", "SQL / PostgreSQL"), view.gaps().stream()
                .map(OpportunityKnowledgeDetailView.GapItem::skill)
                .toList());
        assertTrue(view.transfers().get(0).warning().contains("no demuestran AWS"));
        assertTrue(view.strengths().stream().noneMatch(item ->
                item.skill().equals("AWS") || item.skill().equals("Terraform")));
    }

    @Test
    void backendPlatformSecurityKeepsBackendPrimaryAndSecurityAsSecondaryFocus() {
        CandidateProfile profile = profile("UNDECIDED", "TRAINEE");
        VectorFirstCompatibilityResult result = result(
                "SECURITY_OPS",
                "JUNIOR",
                List.of("Java", "REST"),
                List.of(),
                List.of()
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.BACKEND_JAVA,
                projectEvidence("Java", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("REST APIs", ProfessionalDomain.BACKEND_JAVA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job(
                        "Software Engineer Backend - Platform Security",
                        substantialDescription()
                                + " The team builds backend services with platform security practices and secure SDLC."
                ),
                result,
                evidence
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertEquals("Backend", view.roleFamilyLabel());
        assertEquals("Security Engineering", view.secondaryFocusLabel());
        assertTrue(view.showSecondaryFocus());
        assertTrue(view.secondaryFocusLimit().contains("limite contextual"));
        assertTrue(view.strengths().stream().allMatch(item -> item.evidenceTypeCode().equals("PROJECT")));
    }

    @Test
    void itSupportToCloudShowsPartialTransferWithoutInventingAwsOrTerraform() {
        CandidateProfile profile = profile("IT_SUPPORT", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "DEVOPS",
                "MID",
                List.of("OpenShift", "Linux", "Docker", "Git"),
                List.of("AWS", "Terraform"),
                List.of()
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.SUPPORT,
                workEvidence("IT Support", ProfessionalDomain.SUPPORT),
                workEvidence("OpenShift", ProfessionalDomain.INFRA),
                workEvidence("Linux", ProfessionalDomain.INFRA),
                projectEvidence("Docker", ProfessionalDomain.CLOUD),
                projectEvidence("Git", ProfessionalDomain.BACKEND_JAVA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job("Cloud DevOps Engineer", substantialDescription()),
                result,
                evidence
        );

        assertEquals("PARTIAL_COVERAGE", view.coverageCode());
        assertEquals(1, view.transfers().size());
        assertEquals("IT Support → Cloud / DevOps", view.transfers().get(0).route());
        assertTrue(view.transfers().get(0).warning().contains("no demuestran AWS"));
        assertTrue(view.compactActionLayout());
        assertEquals("is-two-actions", view.actionLayoutClass());
        assertEquals(List.of("AWS", "Terraform"), view.gaps().stream()
                .map(OpportunityKnowledgeDetailView.GapItem::skill)
                .toList());
        assertTrue(view.strengths().stream().noneMatch(item ->
                item.skill().equals("AWS") || item.skill().equals("Terraform")));
    }

    @Test
    void backendProjectsCanDriveFullStackTransitionEvenWhenTargetRoleIsAppSupport() {
        CandidateProfile profile = profile("APP_SUPPORT", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "FULL_STACK",
                "SENIOR",
                List.of("Java", "Spring Boot", "MySQL"),
                List.of(),
                List.of("React", "Kotlin")
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.BACKEND_JAVA,
                projectEvidence("Java", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("Spring Boot", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("REST APIs", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("PostgreSQL", ProfessionalDomain.DATA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job(
                        "Software Developer III - Posventa II",
                        substantialDescription()
                                + " Responsibilities include Java, Spring Boot, REST APIs, MySQL, React and Kotlin."
                ),
                result,
                evidence
        );

        assertEquals("PARTIAL_COVERAGE", view.coverageCode());
        assertEquals("Web Full Stack", view.roleFamilyLabel());
        assertEquals("Backend → Web Full Stack", view.transfers().get(0).route());
        assertFalse(view.showGaps());
        assertTrue(view.strengths().stream().noneMatch(item ->
                item.skill().equals("React") || item.skill().equals("Kotlin")));
    }

    @Test
    void securityFocusedSoftwareEngineerRemainsOutOfScopeForBackendProjectEvidence() {
        CandidateProfile profile = profile("APP_SUPPORT", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "IAM",
                "SENIOR",
                List.of("Docker", "Git", "Python"),
                List.of("IAM", "Kubernetes", "AWS"),
                List.of()
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.BACKEND_JAVA,
                projectEvidence("Docker", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("Git", ProfessionalDomain.BACKEND_JAVA),
                projectEvidence("Python", ProfessionalDomain.BACKEND_JAVA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job(
                        "Senior Security-Focused Software Engineer",
                        substantialDescription()
                                + " The role mentions identity management IAM, cloud security and secure platform work."
                ),
                result,
                evidence
        );

        assertEquals("OUT_OF_SCOPE", view.coverageCode());
        assertEquals("Security / IAM", view.roleFamilyLabel());
        assertFalse(view.showStrengths());
        assertFalse(view.showGaps());
        assertFalse(view.showTransfers());
        assertFalse(view.showActions());
    }

    @Test
    void supportDirectCoverageDoesNotInventWindowsServerEvidence() {
        CandidateProfile profile = profile("IT_SUPPORT", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "IT_SUPPORT",
                "JUNIOR",
                List.of("Windows Server"),
                List.of(),
                List.of()
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.SUPPORT,
                workEvidence("IT Support", ProfessionalDomain.SUPPORT)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job("Technical Support Jr.", substantialDescription()),
                result,
                evidence
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertFalse(view.showStrengths());
        assertTrue(view.showTechnicalMatchLimitNote());
        assertTrue(view.technicalMatchLimitNote().contains("sin evidencia visible"));
    }

    @Test
    void dataBiToDataSupportKeepsFabricAsSpecificGap() {
        CandidateProfile profile = profile("DATA", "JUNIOR");
        VectorFirstCompatibilityResult result = result(
                "IT_SUPPORT",
                "JUNIOR",
                List.of("SQL", "Power BI"),
                List.of(),
                List.of("Microsoft Fabric")
        );
        ProfileEvidenceSummary evidence = evidenceSummary(
                ProfessionalDomain.DATA,
                workEvidence("SQL", ProfessionalDomain.DATA),
                workEvidence("Power BI", ProfessionalDomain.DATA)
        );

        OpportunityKnowledgeDetailView view = mapper.map(
                profile,
                job(
                        "Data Support Engineer",
                        substantialDescription() + " Requiere SQL, Power BI y Microsoft Fabric para reporting."
                ),
                result,
                evidence
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertEquals(List.of("SQL / PostgreSQL", "Power BI"), view.strengths().stream()
                .map(OpportunityKnowledgeDetailView.StrengthItem::skill)
                .toList());
        assertFalse(view.showGaps());
        assertTrue(view.strengths().stream().noneMatch(item -> item.skill().equals("Microsoft Fabric")));
    }

    @Test
    void weakMetadataProducesOnlyLowContextSummary() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("DATA", "JUNIOR"),
                job("Ingeniero de Datos e Infraestructura", "Acerca del empleo"),
                result("DATA", "SENIOR", List.of("SQL"), List.of("AWS", "Terraform"), List.of("Spark")),
                evidenceSummary(ProfessionalDomain.DATA, workEvidence("SQL", ProfessionalDomain.DATA))
        );

        assertEquals("LOW_CONTEXT", view.coverageCode());
        assertTrue(view.lowContext());
        assertFalse(view.showStrengths());
        assertFalse(view.showGaps());
        assertFalse(view.showTransfers());
        assertFalse(view.showActions());
        assertTrue(view.summary().contains("poco detalle verificable"));
    }

    @Test
    void outOfScopeDoesNotClaimDirectProfessionalAffinity() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("BACKEND", "JUNIOR"),
                job("Security Engineer", substantialDescription()),
                result("SECURITY_OPS", "MID", List.of("Java", "Docker", "Linux"), List.of("OWASP"), List.of()),
                evidenceSummary(
                        ProfessionalDomain.BACKEND_JAVA,
                        workEvidence("Java", ProfessionalDomain.BACKEND_JAVA),
                        workEvidence("Docker", ProfessionalDomain.BACKEND_JAVA),
                        workEvidence("Linux", ProfessionalDomain.BACKEND_JAVA)
                )
        );

        assertEquals("OUT_OF_SCOPE", view.coverageCode());
        assertTrue(view.outOfScope());
        assertTrue(view.summary().contains("no hay evidencia suficiente"));
        assertFalse(view.showSupportCards());
        assertFalse(view.showStrengths());
        assertFalse(view.showGaps());
        assertFalse(view.showTransfers());
        assertFalse(view.showActions());
        assertTrue(view.showSharedSignals());
        assertEquals(List.of("Java", "Docker"), view.sharedSignals().stream()
                .map(OpportunityKnowledgeDetailView.SharedSignalItem::skill)
                .toList());
        assertTrue(view.sharedSignals().get(0).warning().contains("No demuestran experiencia"));
    }

    @Test
    void secondaryGapWithoutLiteralRequirementIsNotShownAsReinforcement() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("CLOUD_DEVOPS", "JUNIOR"),
                job("Cloud DevOps Engineer", substantialDescription()),
                result("DEVOPS", "JUNIOR", List.of("Docker"), List.of(), List.of("Azure")),
                evidenceSummary(ProfessionalDomain.CLOUD, projectEvidence("Docker", ProfessionalDomain.CLOUD))
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertFalse(view.showGaps());
        assertFalse(view.showActions());
    }

    @Test
    void explicitCriticalGapIsShownAsReinforcement() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("CLOUD_DEVOPS", "JUNIOR"),
                job("Cloud DevOps Engineer", substantialDescription() + " Requiere Kubernetes para operar servicios."),
                result("DEVOPS", "JUNIOR", List.of("Docker"), List.of("Kubernetes"), List.of()),
                evidenceSummary(ProfessionalDomain.CLOUD, projectEvidence("Docker", ProfessionalDomain.CLOUD))
        );

        assertEquals(List.of("Kubernetes"), view.gaps().stream()
                .map(OpportunityKnowledgeDetailView.GapItem::skill)
                .toList());
        assertEquals(1, view.actions().size());
        assertEquals("Kubernetes", view.actions().get(0).title());
        assertTrue(view.singleActionLayout());
        assertEquals("is-single-action", view.actionLayoutClass());
    }

    @Test
    void singleGapsCardCanRenderFullWidthWithoutStrengthColumn() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("CLOUD_DEVOPS", "JUNIOR"),
                job("Cloud DevOps Engineer", substantialDescription() + " Requiere Kubernetes para operar servicios."),
                result("DEVOPS", "JUNIOR", List.of(), List.of("Kubernetes"), List.of()),
                evidenceSummary(ProfessionalDomain.CLOUD)
        );

        assertEquals("DIRECT_COVERAGE", view.coverageCode());
        assertFalse(view.showStrengths());
        assertTrue(view.showGaps());
        assertFalse(view.strengthsFullWidth());
        assertTrue(view.gapsFullWidth());
    }

    @Test
    void azureContextualSecondaryGapDoesNotCreateAction() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("CLOUD_DEVOPS", "JUNIOR"),
                job(
                        "Cloud DevOps Engineer",
                        substantialDescription() + " Azure aparece como contexto complementario del ecosistema."
                ),
                result("DEVOPS", "JUNIOR", List.of("Docker"), List.of(), List.of("Azure")),
                evidenceSummary(ProfessionalDomain.CLOUD, projectEvidence("Docker", ProfessionalDomain.CLOUD))
        );

        assertTrue(view.gaps().isEmpty());
        assertTrue(view.actions().isEmpty());
    }

    @Test
    void databaseTechnologiesAreGapsOnlyWhenExplicitlyRequired() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("DATA", "JUNIOR"),
                job(
                        "Data Analyst",
                        substantialDescription() + " Requiere Oracle Database y SQL Server para consultas operativas."
                ),
                result("DATA", "JUNIOR", List.of("SQL"), List.of("Oracle Database", "SQL Server"), List.of()),
                evidenceSummary(ProfessionalDomain.DATA, workEvidence("SQL", ProfessionalDomain.DATA))
        );

        assertEquals(List.of("Oracle Database", "SQL Server"), view.gaps().stream()
                .map(OpportunityKnowledgeDetailView.GapItem::skill)
                .toList());
    }

    @Test
    void transfersExposeShortRouteVisibleConceptsAndWarningSeparately() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("BACKEND", "JUNIOR"),
                job("Frontend Developer", substantialDescription() + " Requiere React y consumo de REST APIs."),
                result("FRONTEND", "MID", List.of("REST"), List.of("React"), List.of()),
                evidenceSummary(
                        ProfessionalDomain.BACKEND_JAVA,
                        projectEvidence("REST APIs", ProfessionalDomain.BACKEND_JAVA)
                )
        );

        OpportunityKnowledgeDetailView.TransferItem transfer = view.transfers().get(0);
        assertEquals("Backend → Web Frontend", transfer.route());
        assertTrue(transfer.concepts().size() > 2);
        assertEquals(2, transfer.visibleConcepts().size());
        assertTrue(transfer.warning().contains("no demuestran React"));
    }

    @Test
    void sapS2cBusinessAnalystIsExplicitlyOutOfScopeAndNeverQa() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("QA", "JUNIOR"),
                job(
                        "SAP S2C Business Analyst",
                        substantialDescription() + " SAP S2C process analysis and stakeholder coordination."
                ),
                result("QA", "MID", List.of("SQL"), List.of("Test cases"), List.of()),
                evidenceSummary(ProfessionalDomain.QA, workEvidence("QA", ProfessionalDomain.QA))
        );

        assertEquals("OUT_OF_SCOPE", view.coverageCode());
        assertNull(view.roleFamilyLabel());
        assertFalse(view.showGaps());
        assertFalse(view.showActions());
        assertFalse(view.showTransfers());
    }

    @Test
    void genericBusinessAnalystTitleIsNotExcludedBySapRule() {
        OpportunityKnowledgeDetailMapper.OpportunityRoleResolution role =
                OpportunityKnowledgeDetailMapper.resolveOpportunityRole(
                        "DATA",
                        job("Business Analyst", substantialDescription())
                );

        assertEquals("DATA", role.primaryRole());
    }

    @Test
    void actionsAreDeduplicatedByIntention() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("CLOUD_DEVOPS", "JUNIOR"),
                job("Cloud DevOps Engineer", substantialDescription()),
                result("DEVOPS", "JUNIOR", List.of("AWS", "Azure", "GCP"), List.of(), List.of()),
                evidenceSummary(
                        ProfessionalDomain.CLOUD,
                        declaredEvidence("AWS", ProfessionalDomain.CLOUD),
                        declaredEvidence("Azure", ProfessionalDomain.CLOUD),
                        declaredEvidence("GCP", ProfessionalDomain.CLOUD)
                )
        );

        assertFalse(view.showStrengths());
        assertTrue(view.showTechnicalMatchLimitNote());
        assertEquals(1, view.actions().size());
        assertEquals("Evidencia visible", view.actions().get(0).title());
    }

    @Test
    void spanishApplicationSupportTitleResolvesAppSupportWithoutCreatingCoverage() {
        OpportunityKnowledgeDetailView view = mapper.map(
                profile("DATA", "JUNIOR"),
                job(
                        "Soporte a aplicaciones",
                        substantialDescription() + " Soporte productivo y operaciones de aplicaciones."
                ),
                result("IT_SUPPORT", "JUNIOR", List.of("SQL"), List.of(), List.of()),
                evidenceSummary(ProfessionalDomain.DATA, workEvidence("SQL", ProfessionalDomain.DATA))
        );

        assertEquals("OUT_OF_SCOPE", view.coverageCode());
        assertEquals("Application Support / Operations", view.roleFamilyLabel());
        assertFalse(view.showGaps());
        assertFalse(view.showActions());
        assertFalse(view.showTransfers());
    }

    @Test
    void enrichmentHasNoRankScoreOrOrderFieldsAndCannotChangeResultOrder() {
        VectorFirstCompatibilityResult result = result(
                "BACKEND",
                "JUNIOR",
                List.of("Java"),
                List.of(),
                List.of()
        );
        int vectorRank = result.vectorRank();
        int analysisRank = result.analysisRank();
        double similarity = result.vectorSimilarity();

        mapper.map(
                profile("BACKEND", "JUNIOR"),
                job("Backend Developer", substantialDescription()),
                result,
                evidenceSummary(ProfessionalDomain.BACKEND_JAVA, workEvidence("Java", ProfessionalDomain.BACKEND_JAVA))
        );

        assertEquals(vectorRank, result.vectorRank());
        assertEquals(analysisRank, result.analysisRank());
        assertEquals(vectorRank, analysisRank);
        assertEquals(similarity, result.vectorSimilarity());
        List<String> viewFields = Arrays.stream(OpportunityKnowledgeDetailView.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList();
        assertTrue(viewFields.stream().noneMatch(name ->
                name.contains("rank") || name.contains("score") || name.contains("order") || name.contains("filter")));
    }

    @Test
    void detailViewExposesEveryPropertyUsedByTheTemplate() {
        assertAccessors(
                OpportunityKnowledgeDetailView.class,
                "coverageCode", "coverageLabel", "roleFamilyLabel", "summary",
                "secondaryFocusLabel", "secondaryFocusLimit",
                "technicalMatchLimitNote", "sharedSignals",
                "strengths", "gaps", "transfers", "actions",
                "lowContext", "outOfScope", "showStrengths", "showGaps", "showTransfers", "showActions",
                "showSecondaryFocus", "showTechnicalMatchLimitNote", "showSharedSignals",
                "showSupportCards", "strengthsFullWidth", "gapsFullWidth",
                "singleActionLayout", "compactActionLayout", "timelineActionLayout", "actionLayoutClass"
        );
        assertAccessors(
                OpportunityKnowledgeDetailView.SharedSignalItem.class,
                "skill", "evidenceTypeLabel", "evidenceTypeCode", "warning"
        );
        assertAccessors(
                OpportunityKnowledgeDetailView.StrengthItem.class,
                "skill", "evidenceTypeLabel", "evidenceTypeCode", "explanation", "limit"
        );
        assertAccessors(
                OpportunityKnowledgeDetailView.GapItem.class,
                "skill", "severityLabel", "severityCode", "explanation", "transferNote", "action"
        );
        assertAccessors(
                OpportunityKnowledgeDetailView.TransferItem.class,
                "route", "concepts", "conceptsText", "visibleConcepts", "warning"
        );
        assertAccessors(OpportunityKnowledgeDetailView.ActionItem.class, "title", "text", "reason");
    }

    private static void assertAccessors(Class<?> type, String... names) {
        for (String name : names) {
            assertDoesNotThrow(() -> type.getMethod(name), type.getSimpleName() + "." + name);
        }
    }

    private static CandidateProfile profile(String targetRole, String targetSeniority) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setTargetRole(targetRole);
        profile.setTargetSeniority(targetSeniority);
        return profile;
    }

    private static Job job(String title, String description) {
        Job job = new Job();
        job.setId(14L);
        job.setTitle(title);
        job.setDescription(description);
        return job;
    }

    private static String substantialDescription() {
        return "La oportunidad describe responsabilidades concretas, herramientas requeridas, tareas diarias, "
                + "alcance del equipo y expectativas verificables para desarrollar, mantener y validar soluciones "
                + "técnicas dentro del dominio profesional indicado en el título de la posición.";
    }

    private static ProfileEvidenceSummary evidenceSummary(
            ProfessionalDomain strongDomain,
            ProfessionalSkillEvidence... evidence
    ) {
        return new ProfileEvidenceSummary(
                1L,
                List.of(evidence),
                List.of(),
                List.of(strongDomain),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static ProfessionalSkillEvidence workEvidence(String skill, ProfessionalDomain domain) {
        return evidence(skill, ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalEvidenceStrength.STRONG, domain);
    }

    private static ProfessionalSkillEvidence projectEvidence(String skill, ProfessionalDomain domain) {
        return evidence(skill, ProfessionalEvidenceType.PROJECT, ProfessionalEvidenceStrength.MEDIUM, domain);
    }

    private static ProfessionalSkillEvidence declaredEvidence(String skill, ProfessionalDomain domain) {
        return evidence(skill, ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalEvidenceStrength.WEAK, domain);
    }

    private static ProfessionalSkillEvidence evidence(
            String skill,
            ProfessionalEvidenceType type,
            ProfessionalEvidenceStrength strength,
            ProfessionalDomain domain
    ) {
        return new ProfessionalSkillEvidence(
                skill,
                type,
                strength,
                domain,
                type == ProfessionalEvidenceType.PROJECT
                        ? ProfessionalEvidenceSource.PROJECT
                        : ProfessionalEvidenceSource.CV_TEXT,
                "test",
                "evidencia visible",
                List.of()
        );
    }

    private static VectorFirstCompatibilityResult result(
            String detectedRole,
            String detectedSeniority,
            List<String> matched,
            List<String> missingCritical,
            List<String> missingSecondary
    ) {
        return new VectorFirstCompatibilityResult(
                14L,
                "Opportunity",
                "Example",
                1,
                0.68d,
                1,
                detectedRole,
                detectedSeniority,
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                matched,
                missingCritical,
                missingSecondary,
                List.of(),
                List.of(),
                "Lectura previa existente.",
                CompatibilityConfidence.MEDIUM,
                CompatibilityBucket.GOOD_WITH_MINOR_GAPS,
                1,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
