package com.DataLaburo.web.controller;

import com.DataLaburo.web.analysis.CompatibilityAnalysisException;
import com.DataLaburo.web.analysis.RerankSignal;
import com.DataLaburo.web.analysis.SkillEquivalenceSignal;
import com.DataLaburo.web.analysis.TransferableSkill;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResponse;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityService;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceService;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;
import com.DataLaburo.web.analysis.evidence.ProfileEvidenceSummary;
import com.DataLaburo.web.analysis.evidence.SeniorityByDomain;
import com.DataLaburo.web.analysis.knowledge.OpportunityKnowledgeDetailMapper;
import com.DataLaburo.web.analysis.knowledge.OpportunityKnowledgeDetailView;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.CandidateProfileProjectService;
import com.DataLaburo.web.service.CandidateProfileService;
import com.DataLaburo.web.service.JobPublicationDateService;
import com.DataLaburo.web.service.ProfileImprovementSuggestionService;
import com.DataLaburo.web.service.ProfileRoadmapSuggestionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class ProfileVectorCompatibilityController {
    private static final int DEFAULT_LIMIT = 50;
    private static final ZoneId UI_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-AR"));

    private final CandidateProfileService candidateProfileService;
    private final CandidateProfileProjectService candidateProfileProjectService;
    private final ProfileImprovementSuggestionService profileImprovementSuggestionService;
    private final ProfileRoadmapSuggestionService profileRoadmapSuggestionService;
    private final ProfessionalEvidenceService professionalEvidenceService;
    private final OpportunityKnowledgeDetailMapper opportunityKnowledgeDetailMapper;
    private final JobRepository jobRepository;
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider;
    private final JobPublicationDateService publicationDateService;

    public ProfileVectorCompatibilityController(
            CandidateProfileService candidateProfileService,
            CandidateProfileProjectService candidateProfileProjectService,
            ProfileImprovementSuggestionService profileImprovementSuggestionService,
            ProfileRoadmapSuggestionService profileRoadmapSuggestionService,
            ProfessionalEvidenceService professionalEvidenceService,
            OpportunityKnowledgeDetailMapper opportunityKnowledgeDetailMapper,
            JobRepository jobRepository,
            ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider,
            JobPublicationDateService publicationDateService
    ) {
        this.candidateProfileService = candidateProfileService;
        this.candidateProfileProjectService = candidateProfileProjectService;
        this.profileImprovementSuggestionService = profileImprovementSuggestionService;
        this.profileRoadmapSuggestionService = profileRoadmapSuggestionService;
        this.professionalEvidenceService = professionalEvidenceService;
        this.opportunityKnowledgeDetailMapper = opportunityKnowledgeDetailMapper;
        this.jobRepository = jobRepository;
        this.compatibilityServiceProvider = compatibilityServiceProvider;
        this.publicationDateService = publicationDateService;
    }

    @GetMapping("/profiles/{profileId}/vector-first-compatibility")
    public String vectorFirstCompatibility(
            @PathVariable Long profileId,
            @RequestParam(value = "limit", required = false) String rawLimit,
            Model model
    ) {
        loadCompatibilityPage(profileId, rawLimit, model);
        return "profile-vector-compatibility";
    }

    @GetMapping("/profiles/{profileId}/vector-first-compatibility/jobs/{jobId}")
    public String vectorFirstCompatibilityDetail(
            @PathVariable Long profileId,
            @PathVariable Long jobId,
            @RequestParam(value = "limit", required = false) String rawLimit,
            Model model
    ) {
        CompatibilityPageData pageData = loadCompatibilityPage(profileId, rawLimit, model);
        if (pageData == null) {
            return "profile-vector-compatibility-detail";
        }

        ResultView selectedResult = pageData.results().stream()
                .filter(result -> Objects.equals(result.jobId(), jobId))
                .findFirst()
                .orElse(null);

        model.addAttribute("selectedResult", selectedResult);
        model.addAttribute("selectedJobId", jobId);
        model.addAttribute("relatedResults", pageData.results().stream()
                .filter(result -> !Objects.equals(result.jobId(), jobId))
                .limit(4)
                .toList());

        if (selectedResult == null) {
            model.addAttribute("error", "La oportunidad seleccionada no aparece en este ranking visible.");
        } else {
            VectorFirstCompatibilityResult selectedAnalysis = pageData.response().results().stream()
                    .filter(result -> Objects.equals(result.jobId(), jobId))
                    .findFirst()
                    .orElse(null);
            if (selectedAnalysis != null) {
                OpportunityKnowledgeDetailView knowledgeDetail = opportunityKnowledgeDetailMapper.map(
                        pageData.profile(),
                        pageData.jobsById().get(jobId),
                        selectedAnalysis,
                        pageData.profileEvidenceSummary()
                );
                model.addAttribute("knowledgeDetail", knowledgeDetail);
            }
        }

        return "profile-vector-compatibility-detail";
    }

    private CompatibilityPageData loadCompatibilityPage(
            Long profileId,
            String rawLimit,
            Model model
    ) {
        Optional<CandidateProfile> profile = candidateProfileService.findById(profileId);
        if (profile.isEmpty()) {
            model.addAttribute("profileId", profileId);
            model.addAttribute("error", "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.");
            model.addAttribute("limit", DEFAULT_LIMIT);
            model.addAttribute("results", List.of());
            return null;
        }

        int limit = parseLimit(rawLimit, model);
        model.addAttribute("profile", profile.get());
        model.addAttribute("profileEmbedding", candidateProfileService.findProfileEmbedding(profileId).orElse(null));
        model.addAttribute("limit", limit);
        List<CandidateProfileProject> projects = candidateProfileProjectService.findByProfileId(profileId);
        model.addAttribute("profileProjects", projects);
        ProfileEvidenceSummary profileEvidenceSummary = professionalEvidenceService.summarizeProfile(profile.get(), projects);
        model.addAttribute("profileEvidence", ProfessionalEvidenceSummaryView.from(profileEvidenceSummary));
        model.addAttribute("results", List.of());
        model.addAttribute("profileImprovementSuggestions", List.of());
        model.addAttribute("profileRoadmaps", List.of());

        VectorFirstCompatibilityService compatibilityService = compatibilityServiceProvider.getIfAvailable();
        if (compatibilityService == null) {
            model.addAttribute("error", "La vista vector-first requiere PostgreSQL + pgvector y embeddings BAAI/bge-m3. H2 queda como legado historico/test.");
            return null;
        }

        try {
            VectorFirstCompatibilityResponse response = compatibilityService.analyze(profileId, limit);
            List<VectorFirstCompatibilityResult> responseResults = response.results() == null
                    ? List.of()
                    : response.results();
            Map<Long, Job> jobsById = jobRepository.findAllById(responseResults.stream()
                            .map(VectorFirstCompatibilityResult::jobId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Job::getId, job -> job));
            List<ResultView> results = responseResults.stream()
                    .map(result -> ResultView.from(
                            result,
                            jobsById.get(result.jobId()),
                            profile.get(),
                            projects,
                            profileEvidenceSummary,
                            profileImprovementSuggestionService,
                            publicationDateService
                    ))
                    .toList();
            model.addAttribute("response", response);
            model.addAttribute("profileImprovementSuggestions", profileImprovementSuggestionService
                    .suggestProfile(profile.get())
                    .stream()
                    .map(ProfileImprovementSuggestionView::from)
                    .toList());
            model.addAttribute("profileRoadmaps", profileRoadmapSuggestionService
                    .suggest(profile.get(), projects, responseResults)
                    .stream()
                    .map(ProfileRoadmapView::from)
                    .toList());
            model.addAttribute("results", results);
            model.addAttribute("overview", ResultsOverviewView.from(results, profile.get()));
            model.addAttribute("limit", response.retrieval() == null ? limit : response.retrieval().limit());
            return new CompatibilityPageData(
                    profile.get(),
                    projects,
                    response,
                    results,
                    profileEvidenceSummary,
                    jobsById,
                    response.retrieval() == null ? limit : response.retrieval().limit()
            );
        } catch (CompatibilityAnalysisException e) {
            model.addAttribute("error", friendlyMessage(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            model.addAttribute("error", "No se pudo ejecutar la compatibilidad vector-first. Revisa que PostgreSQL, pgvector y los embeddings READY esten disponibles.");
        }

        return null;
    }

    private static int parseLimit(String rawLimit, Model model) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(rawLimit.trim());
            if (limit <= 0) {
                model.addAttribute("warning", "El limite indicado no es valido. Se usa 50 por defecto.");
                return DEFAULT_LIMIT;
            }
            return Math.min(limit, DEFAULT_LIMIT);
        } catch (NumberFormatException e) {
            model.addAttribute("warning", "El limite indicado no es numerico. Se usa 50 por defecto.");
            return DEFAULT_LIMIT;
        }
    }

    private static String friendlyMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No se pudo ejecutar la compatibilidad vector-first.";
        }
        if (message.contains("No READY PROFILE embedding")) {
            return "Este perfil todavia no tiene embedding listo para BAAI/bge-m3. Procesa embeddings pendientes desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY PROFILE embedding") && message.contains("unsupported vector dimensions")) {
            return "El embedding del perfil no tiene las 1024 dimensiones esperadas para BAAI/bge-m3. Reprocesa el embedding antes de ejecutar esta vista.";
        }
        if (message.contains("No READY JOB embeddings") || message.contains("no READY JOB embeddings")) {
            return "No hay ofertas con embeddings READY para BAAI/bge-m3. Procesa embeddings de JOBS desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY JOB embeddings") && message.contains("unsupported vector dimensions")) {
            return "Las ofertas tienen embeddings con dimensiones incompatibles. Se esperan 1024 dimensiones para BAAI/bge-m3.";
        }
        if (message.contains("Vector search returned no READY JOB embeddings")) {
            return "La busqueda vectorial no encontro ofertas con embeddings disponibles.";
        }
        if (message.contains("Candidate profile not found")) {
            return "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.";
        }
        return message;
    }

    private record CompatibilityPageData(
            CandidateProfile profile,
            List<CandidateProfileProject> projects,
            VectorFirstCompatibilityResponse response,
            List<ResultView> results,
            ProfileEvidenceSummary profileEvidenceSummary,
            Map<Long, Job> jobsById,
            int limit
    ) {
    }

    private record ResultView(
            Long jobId,
            String title,
            String company,
            String companyLogoUrl,
            String companyInitial,
            String locationLabel,
            String modalityLabel,
            String modalityCode,
            String opportunityTypeLabel,
            String opportunityTypeCode,
            String postedAtLabel,
            List<String> visibleSkills,
            String userSummary,
            int vectorRank,
            double vectorSimilarity,
            int analysisRank,
            int closenessScore,
            String scoreBand,
            boolean roleAligned,
            boolean seniorityCompatible,
            boolean hasEvidence,
            String gapLevel,
            String gapLabel,
            String detectedRoleLabel,
            String detectedRoleCode,
            String detectedSeniorityLabel,
            String detectedSeniorityCode,
            String categoryLabel,
            String categoryCode,
            String evidenceLabel,
            String evidenceCode,
            String confidenceLabel,
            String confidenceCode,
            List<String> matchedSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<TransferView> transferableSkills,
            List<String> roadmapSuggestions,
            String explanation,
            String bucketLabel,
            String bucketCode,
            Integer suggestedRerankRank,
            Integer suggestedRankDelta,
            List<DiagnosticChipView> rerankReasons,
            List<DiagnosticChipView> rerankWarnings,
            List<SignalView> rerankSignals,
            List<SkillEquivalenceView> skillEquivalenceSignals,
            RequirementChecklistView requirementChecklist,
            List<ProfileImprovementSuggestionView> improvementSuggestions,
            List<TargetDiagnosticView> targetDiagnostics,
            boolean hasDiagnostic
    ) {
        static ResultView from(
                VectorFirstCompatibilityResult result,
                Job job,
                CandidateProfile profile,
                List<CandidateProfileProject> projects,
                ProfileEvidenceSummary profileEvidenceSummary,
                ProfileImprovementSuggestionService profileImprovementSuggestionService,
                JobPublicationDateService publicationDateService
        ) {
            String bucketCode = result.compatibilityBucket() == null ? null : result.compatibilityBucket().name();
            Integer suggestedRank = result.suggestedRerankRank();
            Integer suggestedDelta = result.suggestedRankDelta();
            List<DiagnosticChipView> warnings = toDiagnosticChips(result.rerankWarnings());
            List<DiagnosticChipView> reasons = toDiagnosticChips(result.rerankReasons());
            List<SignalView> signals = safeList(result.rerankSignals()).stream()
                    .map(SignalView::from)
                    .toList();
            List<TransferView> transferableSkills = safeList(result.transferableSkills()).stream()
                    .filter(Objects::nonNull)
                    .map(TransferView::from)
                    .toList();
            List<SkillEquivalenceView> skillEquivalenceSignals = safeList(result.skillEquivalenceSignals()).stream()
                    .filter(Objects::nonNull)
                    .map(SkillEquivalenceView::from)
                    .toList();
            RequirementChecklistView requirementChecklist = RequirementChecklistView.from(
                    safeList(result.matchedSkills()),
                    safeList(result.missingCriticalSkills()),
                    safeList(result.missingSecondarySkills()),
                    transferableSkills,
                    skillEquivalenceSignals,
                    safeList(result.roadmapSuggestions()),
                    profileEvidenceSummary
            );
            List<TargetDiagnosticView> targetDiagnostics = buildTargetDiagnostics(profile, result);
            int closenessScore = (int) Math.round(result.vectorSimilarity() * 100);
            String scoreBand = closenessScore >= 70 ? "high" : (closenessScore >= 40 ? "medium" : "low");
            String modalityCode = detectModality(job);
            String opportunityTypeCode = detectOpportunityType(job);
            boolean roleAligned = targetDiagnostics.stream().anyMatch(diagnostic -> "Rol alineado".equals(diagnostic.label()));
            boolean seniorityCompatible = targetDiagnostics.stream().anyMatch(diagnostic ->
                    "Seniority compatible".equals(diagnostic.label())
                            || "Seniority abierto".equals(diagnostic.label())
                            || "Seniority no concluyente".equals(diagnostic.label())
            );
            boolean hasEvidence = result.evidenceLevel() != null && result.evidenceLevel() != com.DataLaburo.web.analysis.EvidenceLevel.NO_EVIDENCE;
            String gapLevel = !requirementChecklist.missingCriticalSkills().isEmpty()
                    ? "high"
                    : (!requirementChecklist.missingSecondarySkills().isEmpty() ? "medium" : "low");
            String gapLabel = switch (gapLevel) {
                case "high" -> "Brecha clave";
                case "medium" -> "Brecha menor";
                default -> "Sin brechas visibles";
            };
            boolean hasDiagnostic = bucketCode != null
                    || suggestedRank != null
                    || suggestedDelta != null
                    || !warnings.isEmpty()
                    || !reasons.isEmpty()
                    || !signals.isEmpty()
                    || !skillEquivalenceSignals.isEmpty();

            return new ResultView(
                    result.jobId(),
                    result.title(),
                    result.company(),
                    job == null ? null : job.getCompanyLogoUrl(),
                    ProfileVectorCompatibilityController.companyInitial(result.company(), result.title()),
                    ProfileVectorCompatibilityController.locationLabel(job),
                    labelModality(modalityCode),
                    modalityCode,
                    labelOpportunityType(opportunityTypeCode),
                    opportunityTypeCode,
                    ProfileVectorCompatibilityController.postedAtLabel(job, publicationDateService),
                    ProfileVectorCompatibilityController.visibleSkills(result),
                    ProfileVectorCompatibilityController.userSummary(result, closenessScore, gapLevel),
                    result.vectorRank(),
                    result.vectorSimilarity(),
                    result.analysisRank(),
                    closenessScore,
                    scoreBand,
                    roleAligned,
                    seniorityCompatible,
                    hasEvidence,
                    gapLevel,
                    gapLabel,
                    labelRole(result.detectedRole()),
                    codeOrUnknown(result.detectedRole()),
                    labelSeniority(result.detectedSeniority()),
                    codeOrUnknown(result.detectedSeniority()),
                    labelCategory(result.compatibilityCategory() == null ? null : result.compatibilityCategory().name()),
                    result.compatibilityCategory() == null ? null : result.compatibilityCategory().name(),
                    labelEvidence(result.evidenceLevel() == null ? null : result.evidenceLevel().name()),
                    result.evidenceLevel() == null ? null : result.evidenceLevel().name(),
                    labelConfidence(result.confidence() == null ? null : result.confidence().name()),
                    result.confidence() == null ? null : result.confidence().name(),
                    safeList(result.matchedSkills()),
                    safeList(result.missingCriticalSkills()),
                    safeList(result.missingSecondarySkills()),
                    transferableSkills,
                    safeList(result.roadmapSuggestions()),
                    result.explanation(),
                    labelBucket(bucketCode),
                    bucketCode,
                    suggestedRank,
                    suggestedDelta,
                    reasons,
                    warnings,
                    signals,
                    skillEquivalenceSignals,
                    requirementChecklist,
                    profileImprovementSuggestionService.suggest(profile, projects, result).stream()
                            .map(ProfileImprovementSuggestionView::from)
                            .toList(),
                    targetDiagnostics,
                    hasDiagnostic
            );
        }
    }

    private record ResultsOverviewView(
            int offersAnalyzed,
            int strongMatches,
            int visibleGaps,
            int averageScore,
            int bestScore,
            String searchModeLabel,
            String searchModeDescription,
            String profileUpdatedLabel
    ) {
        static ResultsOverviewView from(List<ResultView> results, CandidateProfile profile) {
            long strongMatches = safeList(results).stream()
                    .filter(result -> result.closenessScore() >= 60)
                    .count();
            long visibleGaps = safeList(results).stream()
                    .filter(result -> !"low".equals(result.gapLevel()))
                    .count();
            int averageScore = safeList(results).isEmpty()
                    ? 0
                    : (int) Math.round(safeList(results).stream()
                    .mapToInt(ResultView::closenessScore)
                    .average()
                    .orElse(0));
            int bestScore = safeList(results).stream()
                    .mapToInt(ResultView::closenessScore)
                    .max()
                    .orElse(0);
            String modeCode = profile == null ? "FOCUSED" : codeOrDefault(profile.getSearchMode(), "FOCUSED");
            return new ResultsOverviewView(
                    safeList(results).size(),
                    (int) strongMatches,
                    (int) visibleGaps,
                    averageScore,
                    bestScore,
                    labelSearchMode(modeCode),
                    switch (modeCode) {
                        case "BALANCED" -> "Mantiene tu foco principal y abre oportunidades cercanas sin reordenar.";
                        case "EXPLORATORY" -> "Amplia la lectura del perfil sin cambiar el orden activo.";
                        default -> "Prioriza lo mas cercano a tu objetivo actual.";
                    },
                    ProfileVectorCompatibilityController.profileUpdatedLabel(profile)
            );
        }
    }

    private record ProfileImprovementSuggestionView(
            String category,
            String categoryLabel,
            String message,
            String reason,
            int priority
    ) {
        static ProfileImprovementSuggestionView from(
                ProfileImprovementSuggestionService.ProfileImprovementSuggestion suggestion
        ) {
            return new ProfileImprovementSuggestionView(
                    suggestion.category(),
                    labelSuggestionCategory(suggestion.category()),
                    suggestion.message(),
                    suggestion.reason(),
                    suggestion.priority()
            );
        }
    }

    private static String labelSuggestionCategory(String category) {
        return switch (codeOrUnknown(category)) {
            case "EVIDENCE" -> "Evidencia";
            case "LEARNING_GAP" -> "Aprendizaje";
            case "LIGHT_REINFORCEMENT" -> "Refuerzo";
            case "TRANSFER" -> "Transferencia";
            case "PARTIAL_RELATION" -> "Contexto";
            case "PROFILE_METADATA" -> "Perfil";
            case "PROFILE_FOCUS" -> "Foco";
            default -> "Sugerencia";
        };
    }

    private record ProfileRoadmapView(
            String skillOrFamily,
            String title,
            String whyItMatters,
            List<String> initialSteps,
            List<String> evidenceIdeas,
            List<String> relatedSignals,
            String toneLabel
    ) {
        static ProfileRoadmapView from(ProfileRoadmapSuggestionService.ProfileRoadmapSuggestion roadmap) {
            return new ProfileRoadmapView(
                    roadmap.skillOrFamily(),
                    roadmap.title(),
                    roadmap.whyItMatters(),
                    roadmap.initialSteps(),
                    roadmap.evidenceIdeas(),
                    roadmap.relatedSignals(),
                    roadmap.toneLabel()
            );
        }
    }

    private record ProfessionalEvidenceSummaryView(
            List<String> strongDomains,
            List<String> transitionDomains,
            List<SeniorityByDomainView> seniorityByDomain,
            List<ProfessionalEvidenceSkillView> workExperienceSkills,
            List<ProfessionalEvidenceSkillView> projectSkills,
            List<ProfessionalEvidenceSkillView> academicSkills,
            List<ProfessionalEvidenceSkillView> declaredOnlySkills,
            List<ProfessionalEvidenceSkillView> transferableSkills,
            boolean hasItems,
            String note
    ) {
        static ProfessionalEvidenceSummaryView from(ProfileEvidenceSummary summary) {
            if (summary == null) {
                return empty();
            }
            List<ProfessionalEvidenceSkillView> skillEvidence = safeList(summary.skillEvidence()).stream()
                    .map(ProfessionalEvidenceSkillView::fromEvidence)
                    .toList();
            List<ProfessionalEvidenceSkillView> workExperience = skillEvidence.stream()
                    .filter(skill -> skill.evidenceTypeCode().equals(ProfessionalEvidenceType.WORK_EXPERIENCE.name()))
                    .toList();
            List<ProfessionalEvidenceSkillView> project = skillEvidence.stream()
                    .filter(skill -> skill.evidenceTypeCode().equals(ProfessionalEvidenceType.PROJECT.name()))
                    .toList();
            List<ProfessionalEvidenceSkillView> academic = skillEvidence.stream()
                    .filter(skill -> skill.evidenceTypeCode().equals(ProfessionalEvidenceType.ACADEMIC.name()))
                    .toList();
            List<ProfessionalEvidenceSkillView> declaredOnly = skillEvidence.stream()
                    .filter(skill -> skill.evidenceTypeCode().equals(ProfessionalEvidenceType.DECLARED_ONLY.name()))
                    .toList();
            List<ProfessionalEvidenceSkillView> transferable = skillEvidence.stream()
                    .filter(skill -> skill.evidenceTypeCode().equals(ProfessionalEvidenceType.TRANSFERABLE.name()))
                    .toList();
            List<String> strongDomains = safeList(summary.strongDomains()).stream()
                    .map(domain -> labelProfessionalDomain(domain.name()))
                    .distinct()
                    .toList();
            List<String> transitionDomains = safeList(summary.transitionDomains()).stream()
                    .map(domain -> labelProfessionalDomain(domain.name()))
                    .distinct()
                    .toList();
            List<SeniorityByDomainView> seniorityByDomain = safeList(summary.seniorityByDomain()).stream()
                    .map(SeniorityByDomainView::from)
                    .toList();
            boolean hasItems = !strongDomains.isEmpty()
                    || !transitionDomains.isEmpty()
                    || !seniorityByDomain.isEmpty()
                    || !skillEvidence.isEmpty();
            return new ProfessionalEvidenceSummaryView(
                    strongDomains,
                    transitionDomains,
                    seniorityByDomain,
                    workExperience,
                    project,
                    academic,
                    declaredOnly,
                    transferable,
                    hasItems,
                    "Esta lectura ayuda a interpretar el perfil, pero no modifica el ranking semantico."
            );
        }

        private static ProfessionalEvidenceSummaryView empty() {
            return new ProfessionalEvidenceSummaryView(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    "Esta lectura ayuda a interpretar el perfil, pero no modifica el ranking semantico."
            );
        }
    }

    private record ProfessionalEvidenceSkillView(
            String skillName,
            String evidenceTypeLabel,
            String evidenceTypeCode,
            String strengthLabel,
            String strengthCode,
            String domainLabel,
            String sourceLabel,
            String context,
            List<String> warnings,
            boolean weak,
            boolean transferable,
            boolean hasEvidence
    ) {
        static ProfessionalEvidenceSkillView fromEvidence(ProfessionalSkillEvidence evidence) {
            if (evidence == null) {
                return withoutEvidence("");
            }
            String evidenceTypeCode = evidence.evidenceType() == null ? null : evidence.evidenceType().name();
            String strengthCode = evidence.strength() == null ? null : evidence.strength().name();
            return new ProfessionalEvidenceSkillView(
                    evidence.skillName(),
                    labelProfessionalEvidenceType(evidenceTypeCode),
                    codeOrUnknown(evidenceTypeCode),
                    labelProfessionalEvidenceStrength(strengthCode),
                    codeOrUnknown(strengthCode),
                    evidence.domain() == null ? "No detectado" : labelProfessionalDomain(evidence.domain().name()),
                    evidence.sourceLabel(),
                    evidence.context(),
                    safeList(evidence.warnings()),
                    evidence.strength() == ProfessionalEvidenceStrength.WEAK
                            || evidence.evidenceType() == ProfessionalEvidenceType.DECLARED_ONLY,
                    evidence.evidenceType() == ProfessionalEvidenceType.TRANSFERABLE,
                    true
            );
        }

        static ProfessionalEvidenceSkillView fromSkill(String skillName, ProfileEvidenceSummary summary) {
            if (summary == null) {
                return withoutEvidence(skillName);
            }
            return summary.strongestEvidenceFor(skillName)
                    .map(ProfessionalEvidenceSkillView::fromEvidence)
                    .orElseGet(() -> withoutEvidence(skillName));
        }

        private static ProfessionalEvidenceSkillView withoutEvidence(String skillName) {
            return new ProfessionalEvidenceSkillView(
                    skillName,
                    "",
                    "UNKNOWN",
                    "",
                    "UNKNOWN",
                    "",
                    "",
                    "",
                    List.of(),
                    false,
                    false,
                    false
            );
        }
    }

    private record SeniorityByDomainView(
            String domainLabel,
            String seniorityLabel,
            String evidenceTypeLabel,
            String confidenceLabel,
            String reason
    ) {
        static SeniorityByDomainView from(SeniorityByDomain seniority) {
            return new SeniorityByDomainView(
                    seniority.domain() == null ? "No detectado" : labelProfessionalDomain(seniority.domain().name()),
                    labelSeniority(seniority.seniority()),
                    labelProfessionalEvidenceType(seniority.evidenceType() == null ? null : seniority.evidenceType().name()),
                    labelProfessionalEvidenceStrength(seniority.confidence() == null ? null : seniority.confidence().name()),
                    seniority.reason()
            );
        }
    }

    private record TransferView(
            String from,
            String to,
            String strengthLabel,
            String strengthCode,
            String reason
    ) {
        static TransferView from(TransferableSkill skill) {
            String strengthCode = skill.strength() == null ? null : skill.strength().name();
            return new TransferView(
                    skill.from(),
                    skill.to(),
                    labelTransferStrength(strengthCode),
                    strengthCode,
                    skill.reason()
            );
        }
    }

    private record SignalView(
            String name,
            String label,
            String polarityLabel,
            String polarityCode,
            String detail
    ) {
        static SignalView from(RerankSignal signal) {
            String polarityCode = signal.polarity() == null ? null : signal.polarity().name();
            return new SignalView(
                    signal.name(),
                    labelSignal(signal.name()),
                    labelPolarity(polarityCode),
                    polarityCode,
                    signal.detail()
            );
        }
    }

    private record DiagnosticChipView(
            String label,
            String detail
    ) {
    }

    private record SkillEquivalenceView(
            String candidateSkill,
            String targetSkill,
            String relationLabel,
            String relationCode,
            String reason
    ) {
        static SkillEquivalenceView from(SkillEquivalenceSignal signal) {
            return new SkillEquivalenceView(
                    signal.candidateSkill(),
                    signal.targetSkill(),
                    labelSkillRelation(signal.relation()),
                    signal.relation(),
                    signal.reason()
            );
        }
    }

    private record RequirementChecklistView(
            List<ProfessionalEvidenceSkillView> presentSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<TransferView> transferableSkills,
            List<SkillEquivalenceView> partialRelations,
            List<String> suggestions,
            boolean hasItems
    ) {
        static RequirementChecklistView from(
                List<String> presentSkills,
                List<String> missingCriticalSkills,
                List<String> missingSecondarySkills,
                List<TransferView> transferableSkills,
                List<SkillEquivalenceView> partialRelations,
                List<String> suggestions,
                ProfileEvidenceSummary profileEvidenceSummary
        ) {
            List<ProfessionalEvidenceSkillView> safePresent = nonBlankStrings(presentSkills).stream()
                    .map(skill -> ProfessionalEvidenceSkillView.fromSkill(skill, profileEvidenceSummary))
                    .filter(skill -> !skill.transferable())
                    .toList();
            List<String> safeMissingCritical = nonBlankStrings(missingCriticalSkills);
            List<String> safeMissingSecondary = nonBlankStrings(missingSecondarySkills);
            List<TransferView> safeTransferable = safeList(transferableSkills);
            List<SkillEquivalenceView> safePartialRelations = safeList(partialRelations);
            List<String> limitedSuggestions = nonBlankStrings(suggestions).stream()
                    .limit(3)
                    .toList();
            boolean hasItems = !safePresent.isEmpty()
                    || !safeMissingCritical.isEmpty()
                    || !safeMissingSecondary.isEmpty()
                    || !safeTransferable.isEmpty()
                    || !safePartialRelations.isEmpty()
                    || !limitedSuggestions.isEmpty();
            return new RequirementChecklistView(
                    safePresent,
                    safeMissingCritical,
                    safeMissingSecondary,
                    safeTransferable,
                    safePartialRelations,
                    limitedSuggestions,
                    hasItems
            );
        }
    }

    private record TargetDiagnosticView(
            String label,
            String detail,
            String category
    ) {
    }

    private static List<TargetDiagnosticView> buildTargetDiagnostics(CandidateProfile profile, VectorFirstCompatibilityResult result) {
        if (profile == null || result == null) {
            return List.of();
        }

        String targetRole = codeOrDefault(profile.getTargetRole(), "UNDECIDED");
        String targetSeniority = codeOrDefault(profile.getTargetSeniority(), "ANY");
        String searchMode = codeOrDefault(profile.getSearchMode(), "FOCUSED");
        String detectedRole = codeOrUnknown(result.detectedRole());
        String detectedSeniority = codeOrUnknown(result.detectedSeniority());

        List<TargetDiagnosticView> out = new java.util.ArrayList<>();
        out.add(roleDiagnostic(targetRole, detectedRole, searchMode));
        out.add(seniorityDiagnostic(targetSeniority, detectedSeniority));
        if (isAdjacentRole(targetRole, detectedRole) && !"FOCUSED".equals(searchMode)) {
            out.add(new TargetDiagnosticView(
                    "Oportunidad adyacente",
                    "El modo de busqueda permite revisar roles cercanos al objetivo.",
                    "adjacent"
            ));
        }
        out.add(new TargetDiagnosticView(
                "Modo: " + labelSearchMode(searchMode),
                modeDetail(searchMode),
                "mode"
        ));
        return out;
    }

    private static TargetDiagnosticView roleDiagnostic(String targetRole, String detectedRole, String searchMode) {
        if ("UNDECIDED".equals(targetRole)) {
            return new TargetDiagnosticView(
                    "Rol objetivo sin definir",
                    "El perfil no prioriza un rol especifico.",
                    "neutral"
            );
        }
        if (rolesAligned(targetRole, detectedRole)) {
            return new TargetDiagnosticView(
                    "Rol alineado",
                    "La oferta detectada coincide con el rol objetivo.",
                    "positive"
            );
        }
        if (isAdjacentRole(targetRole, detectedRole)) {
            String detail = "FOCUSED".equals(searchMode)
                    ? "Es un rol cercano, pero no es el foco principal declarado."
                    : "Es un rol cercano al objetivo declarado.";
            return new TargetDiagnosticView("Oportunidad adyacente", detail, "adjacent");
        }
        return new TargetDiagnosticView(
                "Rol no prioritario",
                "La oferta detectada no coincide con el rol objetivo.",
                "warning"
        );
    }

    private static TargetDiagnosticView seniorityDiagnostic(String targetSeniority, String detectedSeniority) {
        if ("ANY".equals(targetSeniority)) {
            return new TargetDiagnosticView(
                    "Seniority abierto",
                    "El perfil acepta evaluar distintos niveles.",
                    "neutral"
            );
        }
        int targetRank = seniorityRank(targetSeniority);
        int detectedRank = seniorityRank(detectedSeniority);
        if (targetRank <= 0 || detectedRank <= 0) {
            return new TargetDiagnosticView(
                    "Seniority no concluyente",
                    "No hay senales suficientes para comparar el seniority.",
                    "neutral"
            );
        }
        if (targetRank == detectedRank || Math.abs(targetRank - detectedRank) == 1) {
            return new TargetDiagnosticView(
                    "Seniority compatible",
                    "El seniority detectado esta cerca del objetivo.",
                    "positive"
            );
        }
        if (targetRank > detectedRank) {
            return new TargetDiagnosticView(
                    "Posible sobrecalificacion",
                    "El objetivo declarado esta por encima del seniority detectado en la oferta.",
                    "warning"
            );
        }
        return new TargetDiagnosticView(
                "Seniority aspiracional",
                "La oferta parece pedir un nivel superior al objetivo declarado.",
                "warning"
        );
    }

    private static String userSummary(VectorFirstCompatibilityResult result, int closenessScore, String gapLevel) {
        String categoryCode = result.compatibilityCategory() == null ? "UNKNOWN" : result.compatibilityCategory().name();
        if (closenessScore >= 70 && "low".equals(gapLevel)) {
            return "Encaje fuerte para priorizar primero en tu busqueda.";
        }
        if (closenessScore >= 70) {
            return "Muy buen encaje con algunos puntos puntuales para reforzar.";
        }
        if (closenessScore >= 55 && "high".equals(gapLevel)) {
            return "Buen punto de partida, aunque conviene revisar brechas visibles antes de priorizar.";
        }
        if (closenessScore >= 55) {
            return "Oportunidad cercana para explorar con calma y comparar mejor el detalle.";
        }
        if ("TRANSFERABLE_OPPORTUNITY".equals(categoryCode) || "ASPIRATIONAL_MATCH".equals(categoryCode)) {
            return "Oportunidad parcial o transferible para mirar en detalle antes de descartarla.";
        }
        return "Lectura orientativa para decidir si te conviene profundizar en esta oportunidad.";
    }

    private static List<String> visibleSkills(VectorFirstCompatibilityResult result) {
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        for (String skill : nonBlankStrings(result.matchedSkills())) {
            String normalized = skill.trim();
            if (normalized.length() > 34) {
                continue;
            }
            visible.add(normalized);
            if (visible.size() == 5) {
                break;
            }
        }
        return visible.stream().toList();
    }

    private static String companyInitial(String company, String title) {
        String base = firstNonBlank(company, title, "Oportunidad");
        return base.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String locationLabel(Job job) {
        return job == null ? null : firstNonBlank(job.getLocation(), job.getLocationRaw());
    }

    private static String postedAtLabel(Job job, JobPublicationDateService publicationDateService) {
        if (job == null) {
            return null;
        }
        String posted = publicationDateService.labelFor(job).orElse(null);
        if (posted != null) {
            return posted;
        }
        if (job.getCreatedAt() == null) {
            return null;
        }
        return "Actualizada " + SHORT_DATE_FORMATTER.format(job.getCreatedAt().atZone(UI_ZONE));
    }

    private static String profileUpdatedLabel(CandidateProfile profile) {
        if (profile == null) {
            return "Perfil listo para revisar";
        }
        if (profile.getUpdatedAt() != null) {
            return "Actualizado " + SHORT_DATE_FORMATTER.format(profile.getUpdatedAt().atZone(UI_ZONE));
        }
        if (profile.getCreatedAt() != null) {
            return "Creado " + SHORT_DATE_FORMATTER.format(profile.getCreatedAt().atZone(UI_ZONE));
        }
        return "Perfil listo para revisar";
    }

    private static String detectModality(Job job) {
        String text = normalizeForSearch((job == null ? "" : safeText(firstNonBlank(job.getLocation(), job.getLocationRaw())))
                + " "
                + descriptiveText(job));
        if (containsAny(text, "remoto", "remote")) {
            return "REMOTE";
        }
        if (containsAny(text, "hibrido", "hybrid")) {
            return "HYBRID";
        }
        if (containsAny(text, "presencial", "onsite", "on site", "oficina")) {
            return "ONSITE";
        }
        return "UNKNOWN";
    }

    private static String detectOpportunityType(Job job) {
        String text = normalizeForSearch((job == null ? "" : safeText(firstNonBlank(job.getTitle())))
                + " "
                + descriptiveText(job));
        if (containsAny(text, "full time", "fulltime", "jornada completa", "tiempo completo")) {
            return "FULLTIME";
        }
        if (containsAny(text, "part time", "parttime", "medio tiempo", "tiempo parcial")) {
            return "PARTTIME";
        }
        if (containsAny(text, "contrato", "contract", "contractor", "temporary", "temp")) {
            return "CONTRACT";
        }
        if (containsAny(text, "freelance", "autonomo", "independiente")) {
            return "FREELANCE";
        }
        return "UNKNOWN";
    }

    private static String descriptiveText(Job job) {
        if (job == null) {
            return "";
        }
        return String.join(" ",
                safeText(job.getDescription()),
                safeText(job.getVisibleText()),
                safeText(job.getRequirementsText())
        );
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(normalizeForSearch(term))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u')
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<DiagnosticChipView> toDiagnosticChips(List<String> values) {
        return nonBlankStrings(values).stream()
                .map(value -> new DiagnosticChipView(labelDiagnosticText(value), value))
                .distinct()
                .toList();
    }

    private static List<String> nonBlankStrings(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static String codeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String codeOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String labelCategory(String code) {
        return switch (codeOrUnknown(code)) {
            case "STRONG_MATCH" -> "Match fuerte";
            case "GOOD_MATCH_WITH_MINOR_GAPS" -> "Buen match con brechas menores";
            case "TRANSFERABLE_OPPORTUNITY" -> "Oportunidad transferible";
            case "ASPIRATIONAL_MATCH" -> "Match aspiracional";
            case "KEYWORD_MATCH_RISK" -> "Riesgo de match por palabras clave";
            case "LEARNING_ROADMAP_ONLY" -> "Roadmap de aprendizaje";
            case "LOW_FIT" -> "Baja compatibilidad";
            default -> "No detectado";
        };
    }

    private static String labelEvidence(String code) {
        return switch (codeOrUnknown(code)) {
            case "WORK_EXPERIENCE" -> "Experiencia laboral";
            case "PROJECT" -> "Proyecto";
            case "ACADEMIC" -> "Academica";
            case "CERTIFICATION" -> "Certificacion";
            case "MENTIONED_ONLY" -> "Solo mencionada";
            case "TRANSFERABLE" -> "Transferible";
            case "NO_EVIDENCE" -> "Sin evidencia suficiente";
            default -> "No detectado";
        };
    }

    private static String labelProfessionalEvidenceType(String code) {
        return switch (codeOrUnknown(code)) {
            case "WORK_EXPERIENCE" -> "Experiencia laboral";
            case "PROJECT" -> "Proyecto";
            case "ACADEMIC" -> "Academica";
            case "DECLARED_ONLY" -> "Declarada";
            case "TRANSFERABLE" -> "Transferible";
            case "MISSING" -> "Brecha";
            default -> "No detectado";
        };
    }

    private static String labelProfessionalEvidenceStrength(String code) {
        return switch (codeOrUnknown(code)) {
            case "STRONG" -> "Fuerte";
            case "MEDIUM" -> "Media";
            case "WEAK" -> "Debil";
            case "NONE" -> "Sin evidencia";
            default -> "No detectada";
        };
    }

    private static String labelProfessionalDomain(String code) {
        return switch (codeOrUnknown(code)) {
            case "BACKEND_JAVA" -> "Backend Java";
            case "BACKEND_DOTNET" -> "Backend .NET";
            case "SUPPORT" -> "Soporte IT";
            case "APP_SUPPORT" -> "Soporte de aplicaciones";
            case "INFRA" -> "Infraestructura";
            case "CLOUD" -> "Cloud";
            case "DATA" -> "Data/BI";
            case "FRONTEND" -> "Frontend";
            case "QA" -> "QA";
            case "SECURITY" -> "Seguridad";
            default -> "No detectado";
        };
    }

    private static String labelConfidence(String code) {
        return switch (codeOrUnknown(code)) {
            case "HIGH" -> "Alta";
            case "MEDIUM" -> "Media";
            case "LOW" -> "Baja";
            default -> "No detectada";
        };
    }

    private static String labelRole(String code) {
        return switch (codeOrUnknown(code)) {
            case "BACKEND" -> "Backend";
            case "DOTNET_BACKEND" -> ".NET backend";
            case "FRONTEND" -> "Frontend";
            case "FULL_STACK" -> "Full stack";
            case "DOTNET_FULLSTACK" -> ".NET full stack";
            case "DATA" -> "Data/BI";
            case "DATABASE" -> "Base de datos";
            case "IT_SUPPORT" -> "Soporte IT";
            case "APP_SUPPORT" -> "Soporte de aplicaciones";
            case "CLOUD" -> "Cloud";
            case "DEVOPS" -> "DevOps";
            case "QA" -> "QA";
            case "SECURITY_OPS", "IAM" -> "Seguridad/IAM";
            default -> "No detectado";
        };
    }

    private static String labelSeniority(String code) {
        return switch (codeOrUnknown(code)) {
            case "TRAINEE" -> "Trainee";
            case "JUNIOR" -> "Junior";
            case "MID" -> "Semi senior";
            case "SENIOR" -> "Senior";
            case "LEAD" -> "Lead";
            default -> "No detectado";
        };
    }

    private static String labelSearchMode(String code) {
        return switch (codeOrUnknown(code)) {
            case "FOCUSED" -> "Enfocado";
            case "BALANCED" -> "Balanceado";
            case "EXPLORATORY" -> "Exploratorio";
            default -> "No detectado";
        };
    }

    private static String labelModality(String code) {
        return switch (codeOrUnknown(code)) {
            case "REMOTE" -> "Remoto";
            case "HYBRID" -> "Hibrido";
            case "ONSITE" -> "Presencial";
            default -> null;
        };
    }

    private static String labelOpportunityType(String code) {
        return switch (codeOrUnknown(code)) {
            case "FULLTIME" -> "Full time";
            case "PARTTIME" -> "Part time";
            case "CONTRACT" -> "Contrato";
            case "FREELANCE" -> "Freelance";
            default -> null;
        };
    }

    private static String modeDetail(String code) {
        return switch (codeOrUnknown(code)) {
            case "BALANCED" -> "Se muestran senales de foco y oportunidades cercanas sin reordenar.";
            case "EXPLORATORY" -> "Se permite revisar oportunidades amplias sin filtrar resultados.";
            default -> "Se prioriza leer el foco declarado sin filtrar ni reordenar.";
        };
    }

    private static boolean rolesAligned(String targetRole, String detectedRole) {
        String target = normalizeRole(targetRole);
        String detected = normalizeRole(detectedRole);
        return !"UNKNOWN".equals(target) && target.equals(detected);
    }

    private static boolean isAdjacentRole(String targetRole, String detectedRole) {
        String target = normalizeRole(targetRole);
        String detected = normalizeRole(detectedRole);
        if ("UNKNOWN".equals(target) || "UNKNOWN".equals(detected) || target.equals(detected)) {
            return false;
        }
        return switch (target) {
            case "BACKEND" -> detected.equals("FULL_STACK") || detected.equals("DATABASE") || detected.equals("CLOUD") || detected.equals("DEVOPS");
            case "FRONTEND" -> detected.equals("FULL_STACK");
            case "FULL_STACK" -> detected.equals("BACKEND") || detected.equals("FRONTEND");
            case "DATA" -> detected.equals("DATABASE") || detected.equals("BACKEND");
            case "DATABASE" -> detected.equals("DATA") || detected.equals("BACKEND");
            case "IT_SUPPORT" -> detected.equals("APP_SUPPORT") || detected.equals("SECURITY_OPS") || detected.equals("IAM");
            case "APP_SUPPORT" -> detected.equals("IT_SUPPORT") || detected.equals("DATABASE");
            case "SECURITY_OPS", "IAM" -> detected.equals("IT_SUPPORT") || detected.equals("APP_SUPPORT") || detected.equals("CLOUD");
            case "DEVOPS", "CLOUD" -> detected.equals("BACKEND") || detected.equals("SECURITY_OPS");
            case "QA" -> detected.equals("BACKEND") || detected.equals("FULL_STACK");
            default -> false;
        };
    }

    private static String normalizeRole(String role) {
        return switch (codeOrUnknown(role)) {
            case "DOTNET_BACKEND" -> "BACKEND";
            case "DOTNET_FULLSTACK" -> "FULL_STACK";
            default -> codeOrUnknown(role);
        };
    }

    private static int seniorityRank(String seniority) {
        return switch (codeOrUnknown(seniority)) {
            case "TRAINEE" -> 1;
            case "JUNIOR" -> 2;
            case "MID" -> 3;
            case "SENIOR" -> 4;
            case "LEAD" -> 5;
            default -> 0;
        };
    }

    private static String labelBucket(String code) {
        return switch (codeOrUnknown(code)) {
            case "READY_NOW" -> "Listo para postular";
            case "GOOD_WITH_MINOR_GAPS" -> "Bueno con brechas menores";
            case "TRANSFERABLE" -> "Transferible";
            case "ASPIRATIONAL" -> "Aspiracional";
            case "WEAK_MATCH" -> "Match debil";
            case "LOW_FIT" -> "Baja compatibilidad";
            default -> "N/A";
        };
    }

    private static String labelTransferStrength(String code) {
        return switch (codeOrUnknown(code)) {
            case "STRONG" -> "Fuerte";
            case "PARTIAL" -> "Parcial";
            default -> "No detectado";
        };
    }

    private static String labelPolarity(String code) {
        return switch (codeOrUnknown(code)) {
            case "POSITIVE" -> "Positiva";
            case "NEGATIVE" -> "Negativa";
            case "NEUTRAL" -> "Neutral";
            default -> "No detectado";
        };
    }

    private static String labelDiagnosticText(String value) {
        String normalized = normalizeDiagnosticText(value);
        if (normalized.contains("rol periferico")) {
            return "Rol periferico";
        }
        if (normalized.contains("seniority superior")) {
            return "Seniority superior al objetivo";
        }
        if (normalized.contains("falta de skills matcheadas")
                || normalized.contains("matches genericos")
                || normalized.contains("pocas coincidencias")) {
            return "Pocas coincidencias directas";
        }
        if (normalized.contains("gaps criticos") || normalized.contains("brechas criticas")) {
            return "Brechas criticas";
        }
        if (normalized.contains("transferible") || normalized.contains("transferibilidad")) {
            return "Se mantiene por transferibilidad";
        }
        if (normalized.contains("evidencia debil") || normalized.contains("no_evidence") || normalized.contains("mentioned_only")) {
            return "Evidencia debil";
        }
        if (normalized.contains("deteccion dudosa")
                || normalized.contains("baja confianza")
                || normalized.contains("rol no determinado")) {
            return "Deteccion con baja confianza";
        }
        return compactDiagnosticText(value);
    }

    private static String labelSignal(String value) {
        return switch (codeOrUnknown(value)) {
            case "ROLE_ALIGNED" -> "Rol alineado";
            case "ROLE_PERIPHERAL" -> "Rol periferico";
            case "SENIORITY_ABOVE_TARGET" -> "Seniority superior al objetivo";
            case "LOW_DIRECT_MATCHES" -> "Pocas coincidencias directas";
            case "CRITICAL_GAPS" -> "Brechas criticas";
            case "TRANSFERABLE_MATCH" -> "Se mantiene por transferibilidad";
            case "WEAK_EVIDENCE" -> "Evidencia debil";
            case "LOW_CONFIDENCE_DETECTION" -> "Deteccion con baja confianza";
            default -> compactDiagnosticText(value);
        };
    }

    private static String compactDiagnosticText(String value) {
        if (value == null || value.isBlank()) {
            return "Diagnostico";
        }
        String cleaned = value.trim();
        int colon = cleaned.indexOf(':');
        if (colon > 0) {
            cleaned = cleaned.substring(0, colon).trim();
        }
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.replace('_', ' ');
        if (cleaned.length() <= 44) {
            return cleaned;
        }
        return cleaned.substring(0, 41).trim() + "...";
    }

    private static String normalizeDiagnosticText(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static String labelSkillRelation(String code) {
        return switch (codeOrUnknown(code)) {
            case "PARTIAL_EQUIVALENCE" -> "Equivalencia parcial";
            case "PARTIAL_TRANSFER" -> "Transferencia parcial";
            case "RELATED" -> "Relacionado";
            case "CONTEXTUAL" -> "Contextual";
            default -> "Relacion parcial";
        };
    }
}
