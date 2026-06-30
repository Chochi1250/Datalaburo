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
import com.DataLaburo.web.analysis.evidence.ProfessionalDomain;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceService;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceSource;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;
import com.DataLaburo.web.analysis.evidence.ProfileEvidenceSummary;
import com.DataLaburo.web.analysis.evidence.SeniorityByDomain;
import com.DataLaburo.web.analysis.knowledge.OpportunityKnowledgeDetailMapper;
import com.DataLaburo.web.analysis.knowledge.OpportunityKnowledgeDetailView;
import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.DocumentEmbeddingOwnerType;
import com.DataLaburo.web.embedding.DocumentEmbeddingSectionType;
import com.DataLaburo.web.embedding.DocumentEmbeddingStatus;
import com.DataLaburo.web.embedding.EmbeddingTextNormalizer;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.model.ProjectEvidenceType;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.ProfileImprovementSuggestionService;
import com.DataLaburo.web.service.ProfileRoadmapSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.ConcurrentModel;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final ProfessionalEvidenceService professionalEvidenceService = mock(ProfessionalEvidenceService.class);
    private final OpportunityKnowledgeDetailMapper opportunityKnowledgeDetailMapper = mock(OpportunityKnowledgeDetailMapper.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final VectorFirstCompatibilityService compatibilityService = mock(VectorFirstCompatibilityService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider = mock(ObjectProvider.class);
    private final ProfileVectorCompatibilityController controller = new ProfileVectorCompatibilityController(
            candidateProfileService,
            candidateProfileProjectService,
            profileImprovementSuggestionService,
            profileRoadmapSuggestionService,
            professionalEvidenceService,
            opportunityKnowledgeDetailMapper,
            jobRepository,
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
        profile.setUpdatedAt(java.time.Instant.parse("2026-06-10T20:45:39Z"));
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
                List.of("Java", "Spring Boot", "AWS", "Cloud"),
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
        ProfileEvidenceSummary professionalEvidence = professionalEvidenceSummary();
        when(professionalEvidenceService.summarizeProfile(profile, List.of(project))).thenReturn(professionalEvidence);
        when(jobRepository.findAllById(List.of(14L, 15L))).thenReturn(List.of(
                job(14L, "Example", "Buenos Aires", "Remoto", "hace 2 dias"),
                job(15L, "Example", "Cordoba", "Hibrido", "hace 5 dias")
        ));
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
        Object profileEvidence = model.getAttribute("profileEvidence");
        assertEquals(true, invoke(profileEvidence, "hasItems"));
        assertEquals("Esta lectura ayuda a interpretar el perfil, pero no modifica el ranking semantico.", invoke(profileEvidence, "note"));
        List<?> workExperienceSkills = (List<?>) invoke(profileEvidence, "workExperienceSkills");
        List<?> projectSkills = (List<?>) invoke(profileEvidence, "projectSkills");
        List<?> academicSkills = (List<?>) invoke(profileEvidence, "academicSkills");
        List<?> declaredOnlySkills = (List<?>) invoke(profileEvidence, "declaredOnlySkills");
        List<?> transferableEvidence = (List<?>) invoke(profileEvidence, "transferableSkills");
        assertEquals("Java", invoke(workExperienceSkills.get(0), "skillName"));
        assertEquals("WORK_EXPERIENCE", invoke(workExperienceSkills.get(0), "evidenceTypeCode"));
        assertEquals("Spring Boot", invoke(projectSkills.get(0), "skillName"));
        assertEquals("PROJECT", invoke(projectSkills.get(0), "evidenceTypeCode"));
        assertEquals("SQL", invoke(academicSkills.get(0), "skillName"));
        assertEquals("ACADEMIC", invoke(academicSkills.get(0), "evidenceTypeCode"));
        assertEquals("AWS", invoke(declaredOnlySkills.get(0), "skillName"));
        assertEquals("DECLARED_ONLY", invoke(declaredOnlySkills.get(0), "evidenceTypeCode"));
        assertEquals(true, invoke(declaredOnlySkills.get(0), "weak"));
        assertEquals("Cloud", invoke(transferableEvidence.get(0), "skillName"));
        assertEquals("TRANSFERABLE", invoke(transferableEvidence.get(0), "evidenceTypeCode"));

        List<?> rows = (List<?>) model.getAttribute("results");
        assertEquals(1, invoke(rows.get(0), "analysisRank"));
        assertEquals(1, invoke(rows.get(0), "vectorRank"));
        assertEquals("E", invoke(rows.get(0), "companyInitial"));
        assertEquals("Buenos Aires", invoke(rows.get(0), "locationLabel"));
        assertEquals("Remoto", invoke(rows.get(0), "modalityLabel"));
        assertEquals("hace 2 dias", invoke(rows.get(0), "postedAtLabel"));
        assertEquals("Buen punto de partida, aunque conviene revisar brechas visibles antes de priorizar.", invoke(rows.get(0), "userSummary"));
        List<?> visibleSkills = (List<?>) invoke(rows.get(0), "visibleSkills");
        assertEquals(List.of("Java", "Spring Boot", "AWS", "Cloud"), visibleSkills);
        Object checklist = invoke(rows.get(0), "requirementChecklist");

        List<?> presentSkills = (List<?>) invoke(checklist, "presentSkills");
        assertEquals(3, presentSkills.size());
        assertEquals("Java", invoke(presentSkills.get(0), "skillName"));
        assertEquals("WORK_EXPERIENCE", invoke(presentSkills.get(0), "evidenceTypeCode"));
        assertEquals("Spring Boot", invoke(presentSkills.get(1), "skillName"));
        assertEquals("PROJECT", invoke(presentSkills.get(1), "evidenceTypeCode"));
        assertEquals("AWS", invoke(presentSkills.get(2), "skillName"));
        assertEquals("DECLARED_ONLY", invoke(presentSkills.get(2), "evidenceTypeCode"));
        assertEquals(true, invoke(presentSkills.get(2), "weak"));
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
        Object overview = model.getAttribute("overview");
        assertEquals(2, invoke(overview, "offersAnalyzed"));
        assertEquals(2, invoke(overview, "strongMatches"));
        assertEquals(2, invoke(overview, "visibleGaps"));
        assertEquals(66, invoke(overview, "averageScore"));
        assertEquals(68, invoke(overview, "bestScore"));
        assertEquals("Enfocado", invoke(overview, "searchModeLabel"));
        assertEquals("Actualizado 10 jun 2026", invoke(overview, "profileUpdatedLabel"));
        List<?> roadmaps = (List<?>) model.getAttribute("profileRoadmaps");
        assertEquals(1, roadmaps.size());
        assertEquals("Kubernetes", invoke(roadmaps.get(0), "skillOrFamily"));
        verify(compatibilityService).analyze(1L, 20);
        verify(candidateProfileProjectService).findByProfileId(1L);
        verify(professionalEvidenceService).summarizeProfile(profile, List.of(project));
        verify(jobRepository).findAllById(List.of(14L, 15L));
    }

    @Test
    void detailPageUsesSelectedOpportunityWithoutChangingVisibleOrder() throws Exception {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java backend profile");
        profile.setHeadline("Backend Java junior");
        profile.setSummary("Builds APIs and documents project evidence.");
        profile.setDeclaredSkillsText("Java, Spring Boot");
        profile.setUpdatedAt(java.time.Instant.parse("2026-06-10T20:45:39Z"));
        CandidateProfileProject project = project(profile, "Portfolio API");
        ProfileEvidenceSummary profileEvidenceSummary = professionalEvidenceSummary();
        Job selectedJob = job(14L, "Example", "Buenos Aires", "Remoto", "hace 2 dias");
        Job relatedJob = job(15L, "Example", "Cordoba", "Hibrido", "hace 5 dias");
        VectorFirstCompatibilityResult selectedAnalysis = jobResult(
                14L,
                "Backend Engineer",
                1,
                0.68d,
                1,
                List.of("Java", "Spring Boot"),
                List.of("Kubernetes"),
                List.of("AWS")
        );
        VectorFirstCompatibilityResult relatedAnalysis = jobResult(
                15L,
                "Platform Engineer",
                2,
                0.64d,
                2,
                List.of("Java"),
                List.of("Kubernetes"),
                List.of()
        );
        OpportunityKnowledgeDetailView knowledgeDetail = knowledgeDetailView();
        when(candidateProfileService.findById(1L)).thenReturn(Optional.of(profile));
        when(candidateProfileService.findProfileEmbedding(1L)).thenReturn(Optional.empty());
        when(candidateProfileProjectService.findByProfileId(1L)).thenReturn(List.of(project));
        when(professionalEvidenceService.summarizeProfile(profile, List.of(project))).thenReturn(profileEvidenceSummary);
        when(jobRepository.findAllById(List.of(14L, 15L))).thenReturn(List.of(selectedJob, relatedJob));
        when(opportunityKnowledgeDetailMapper.map(profile, selectedJob, selectedAnalysis, profileEvidenceSummary))
                .thenReturn(knowledgeDetail);
        when(compatibilityServiceProvider.getIfAvailable()).thenReturn(compatibilityService);
        when(compatibilityService.analyze(1L, 20)).thenReturn(new VectorFirstCompatibilityResponse(
                1L,
                "BAAI/bge-m3",
                1024,
                new VectorFirstCompatibilityResponse.Retrieval(20, "VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC"),
                List.of(selectedAnalysis, relatedAnalysis)
        ));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.vectorFirstCompatibilityDetail(1L, 14L, "20", model);

        assertEquals("profile-vector-compatibility-detail", viewName);
        assertEquals(14L, model.getAttribute("selectedJobId"));
        Object selectedResult = model.getAttribute("selectedResult");
        assertEquals(14L, invoke(selectedResult, "jobId"));
        assertEquals(1, invoke(selectedResult, "analysisRank"));
        assertEquals(1, invoke(selectedResult, "vectorRank"));
        assertEquals(68, invoke(selectedResult, "closenessScore"));
        assertEquals("medium", invoke(selectedResult, "scoreBand"));
        assertEquals("Buenos Aires", invoke(selectedResult, "locationLabel"));
        assertEquals("Remoto", invoke(selectedResult, "modalityLabel"));
        assertEquals("Buen punto de partida, aunque conviene revisar brechas visibles antes de priorizar.", invoke(selectedResult, "userSummary"));
        assertEquals(knowledgeDetail, model.getAttribute("knowledgeDetail"));
        List<?> relatedResults = (List<?>) model.getAttribute("relatedResults");
        assertEquals(1, relatedResults.size());
        assertEquals(15L, invoke(relatedResults.get(0), "jobId"));
        Object overview = model.getAttribute("overview");
        assertEquals(2, invoke(overview, "offersAnalyzed"));
        assertEquals(2, invoke(overview, "strongMatches"));
        assertEquals(2, invoke(overview, "visibleGaps"));
        assertEquals(66, invoke(overview, "averageScore"));
        verify(compatibilityService).analyze(1L, 20);
        verify(jobRepository).findAllById(List.of(14L, 15L));
        verify(opportunityKnowledgeDetailMapper).map(profile, selectedJob, selectedAnalysis, profileEvidenceSummary);
    }

    @Test
    void templatesClarifyLocalDemoFlowAndKeepDiagnosticsCompact() throws Exception {
        String vectorCompatibilityTemplate = Files.readString(Path.of(
                "src/main/resources/templates/profile-vector-compatibility.html"
        ));
        String vectorCompatibilityDetailTemplate = Files.readString(Path.of(
                "src/main/resources/templates/profile-vector-compatibility-detail.html"
        ));
        String vectorSearchTemplate = Files.readString(Path.of(
                "src/main/resources/templates/vector-search.html"
        ));
        String profileDetailTemplate = Files.readString(Path.of(
                "src/main/resources/templates/profile-detail.html"
        ));

        assertTrue(vectorCompatibilityTemplate.contains(
                "Resultados para tu perfil"
        ));
        assertTrue(vectorCompatibilityTemplate.contains("Puntaje 0-100"));
        assertTrue(vectorCompatibilityTemplate.contains("avatar card.png"));
        assertTrue(vectorCompatibilityTemplate.contains("Ver mi perfil"));
        assertTrue(vectorCompatibilityTemplate.contains("Ver mas contexto del perfil"));
        assertTrue(vectorCompatibilityTemplate.contains("Oportunidades cercanas"));
        assertTrue(vectorCompatibilityTemplate.contains("Puntaje promedio"));
        assertTrue(vectorCompatibilityTemplate.contains("Mejor puntaje"));
        assertTrue(vectorCompatibilityTemplate.contains("data-modality"));
        assertTrue(vectorCompatibilityTemplate.contains("data-carousel"));
        assertTrue(vectorCompatibilityTemplate.contains("Ver detalle"));
        assertTrue(vectorCompatibilityTemplate.contains("analysisRank"));
        assertFalse(vectorCompatibilityTemplate.contains("rankingMode"));

        assertTrue(vectorCompatibilityDetailTemplate.contains("Resumen de la oportunidad"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Lo más relevante"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Coincidencias concretas"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Lo que ya suma a tu perfil"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Lo que conviene reforzar"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Plan de acción recomendado"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Cómo interpretar tu puntaje"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Oportunidades cercanas"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Ver oferta original"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Señales transferibles"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Lectura prudente"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("Señales compartidas, no equivalencia profesional"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("is-prudent-only"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("has-single-support-card"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("is-single-action"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("is-two-actions"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("max-width: 1180px"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("knowledgeDetail.summary"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("knowledgeDetail.strengths"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("knowledgeDetail.gaps"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("knowledgeDetail.transfers"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("knowledgeDetail.actions"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("Ã"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("Â"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("â"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("selectedResult.improvementSuggestions"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("selectedResult.roadmapSuggestions"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("vector-detail-card-grid"));
        assertFalse(vectorCompatibilityDetailTemplate.contains("vector-detail-guided-card"));
        assertTrue(vectorCompatibilityDetailTemplate.contains("selectedResult"));

        assertTrue(vectorSearchTemplate.contains("Empeza tu analisis laboral"));
        assertTrue(vectorSearchTemplate.contains("vector-start-card"));
        assertTrue(vectorSearchTemplate.contains("vector-workbench"));
        assertTrue(vectorSearchTemplate.contains("Pegar CV"));
        assertTrue(vectorSearchTemplate.contains("Usar perfil existente"));
        assertTrue(vectorSearchTemplate.contains("Completar perfil"));
        assertFalse(vectorSearchTemplate.contains("Completar perfil en detalle"));
        assertFalse(vectorSearchTemplate.contains("Crear perfil completo"));
        assertTrue(vectorSearchTemplate.contains("DataLaburo intenta"));
        assertFalse(vectorSearchTemplate.contains("vector-preview-card"));
        assertFalse(vectorSearchTemplate.contains("Alta completa de perfil"));
        assertFalse(vectorSearchTemplate.contains("Estado de embeddings"));

        assertTrue(profileDetailTemplate.contains(
                "Perfil guardado localmente en esta demo. No requiere login ni cuenta de usuario."
        ));
        assertTrue(profileDetailTemplate.contains("Elegir perfil existente"));
        assertTrue(profileDetailTemplate.contains("Ver compatibilidad vectorial"));
    }

    private static ProfileEvidenceSummary professionalEvidenceSummary() {
        return new ProfileEvidenceSummary(
                1L,
                List.of(
                        evidence("Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalEvidenceStrength.STRONG, ProfessionalDomain.BACKEND_JAVA),
                        evidence("Spring Boot", ProfessionalEvidenceType.PROJECT, ProfessionalEvidenceStrength.MEDIUM, ProfessionalDomain.BACKEND_JAVA),
                        evidence("SQL", ProfessionalEvidenceType.ACADEMIC, ProfessionalEvidenceStrength.MEDIUM, ProfessionalDomain.DATA),
                        evidence("Cloud", ProfessionalEvidenceType.TRANSFERABLE, ProfessionalEvidenceStrength.WEAK, ProfessionalDomain.CLOUD),
                        evidence("AWS", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalEvidenceStrength.WEAK, ProfessionalDomain.CLOUD)
                ),
                List.of(new SeniorityByDomain(
                        ProfessionalDomain.BACKEND_JAVA,
                        "JUNIOR",
                        ProfessionalEvidenceType.PROJECT,
                        ProfessionalEvidenceStrength.WEAK,
                        "Evidence is not senior work experience for this domain."
                )),
                List.of(ProfessionalDomain.BACKEND_JAVA),
                List.of(ProfessionalDomain.CLOUD),
                List.of("AWS"),
                List.of()
        );
    }

    private static OpportunityKnowledgeDetailView knowledgeDetailView() {
        return new OpportunityKnowledgeDetailView(
                "PARTIAL_COVERAGE",
                "Cobertura parcial",
                "Cloud / DevOps",
                null,
                null,
                "Hay señales aprovechables para esta transición, aunque faltan evidencias específicas del dominio.",
                null,
                List.of(),
                List.of(new OpportunityKnowledgeDetailView.StrengthItem(
                        "Java",
                        "Laboral",
                        "WORK_EXPERIENCE",
                        "Hay evidencia laboral directa de Java.",
                        null
                )),
                List.of(new OpportunityKnowledgeDetailView.GapItem(
                        "Kubernetes",
                        "Alta",
                        "CRITICAL",
                        "La oferta pide Kubernetes de forma explícita.",
                        "Docker no demuestra Kubernetes.",
                        "Documentar un laboratorio reproducible."
                )),
                List.of(new OpportunityKnowledgeDetailView.TransferItem(
                        "Backend → Cloud / DevOps",
                        List.of("entrega de servicios"),
                        "La transferencia es parcial."
                )),
                List.of(new OpportunityKnowledgeDetailView.ActionItem(
                        "Documentar un laboratorio reproducible.",
                        "Brecha crítica detectada"
                )),
                false,
                false
        );
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
                sourceFor(type),
                "test",
                "test context",
                type == ProfessionalEvidenceType.TRANSFERABLE
                        ? List.of("Transferable signal, not direct evidence.")
                        : List.of()
        );
    }

    private static ProfessionalEvidenceSource sourceFor(ProfessionalEvidenceType type) {
        return switch (type) {
            case WORK_EXPERIENCE, ACADEMIC -> ProfessionalEvidenceSource.CV_TEXT;
            case PROJECT -> ProfessionalEvidenceSource.PROJECT;
            case DECLARED_ONLY -> ProfessionalEvidenceSource.DECLARED_SKILLS;
            case TRANSFERABLE -> ProfessionalEvidenceSource.TRANSFER_RULE;
            case MISSING -> ProfessionalEvidenceSource.GAP_ANALYSIS;
        };
    }

    private static VectorFirstCompatibilityResult jobResult(
            Long jobId,
            String title,
            int vectorRank,
            double similarity,
            int analysisRank,
            List<String> matchedSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills
    ) {
        return new VectorFirstCompatibilityResult(
                jobId,
                title,
                "Example",
                vectorRank,
                similarity,
                analysisRank,
                "BACKEND",
                "MID",
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                List.of(),
                List.of("Documentar evidencia"),
                "La oferta esta cerca semanticamente.",
                CompatibilityConfidence.MEDIUM,
                CompatibilityBucket.GOOD_WITH_MINOR_GAPS,
                analysisRank,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static Job job(Long id, String company, String location, String description, String postedAtText) {
        Job job = new Job();
        job.setId(id);
        job.setTitle("Job " + id);
        job.setCompany(company);
        job.setLocation(location);
        job.setDescription(description);
        job.setPostedAtText(postedAtText);
        return job;
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
