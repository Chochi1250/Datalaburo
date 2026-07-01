package com.DataLaburo.web.analysis;

import com.DataLaburo.web.embedding.DocumentEmbedding;
import com.DataLaburo.web.embedding.EmbeddingTextBuilder;
import com.DataLaburo.web.embedding.EmbeddingVectorSearchRepository;
import com.DataLaburo.web.embedding.EmbeddingVectorSearchResponse;
import com.DataLaburo.web.embedding.EmbeddingVectorSearchResult;
import com.DataLaburo.web.embedding.EmbeddingVectorSearchService;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.repository.CandidateProfileRepository;
import com.DataLaburo.web.repository.JobRepository;
import com.DataLaburo.web.service.RuleBasedEnrichmentService;
import com.DataLaburo.web.service.SkillExtractionService;
import com.DataLaburo.web.service.SkillExtractionService.ExtractedSkills;
import com.DataLaburo.web.service.SkillExtractionService.SkillCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.postgresql.Driver"
)
public class VectorFirstCompatibilityService {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 50;
    static final String STRATEGY = "VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC";

    private static final String EMBEDDING_MODEL = DocumentEmbedding.DEFAULT_EMBEDDING_MODEL;
    private static final int EMBEDDING_DIMENSIONS = DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS;

    private final CandidateProfileRepository candidateProfileRepository;
    private final JobRepository jobRepository;
    private final EmbeddingVectorSearchRepository vectorSearchRepository;
    private final EmbeddingVectorSearchService vectorSearchService;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final SkillExtractionService skillExtractionService;
    private final RuleBasedEnrichmentService ruleBasedEnrichmentService;
    private final GapAnalysisService gapAnalysisService;
    private final SkillEquivalenceService skillEquivalenceService;
    private final TransferabilityService transferabilityService;
    private final CompatibilityExplanationService explanationService;
    private final RerankingDiagnosticService rerankingDiagnosticService;

    public VectorFirstCompatibilityService(
            CandidateProfileRepository candidateProfileRepository,
            JobRepository jobRepository,
            EmbeddingVectorSearchRepository vectorSearchRepository,
            EmbeddingVectorSearchService vectorSearchService,
            EmbeddingTextBuilder embeddingTextBuilder,
            SkillExtractionService skillExtractionService,
            RuleBasedEnrichmentService ruleBasedEnrichmentService,
            GapAnalysisService gapAnalysisService,
            SkillEquivalenceService skillEquivalenceService,
            TransferabilityService transferabilityService,
            CompatibilityExplanationService explanationService,
            RerankingDiagnosticService rerankingDiagnosticService
    ) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.jobRepository = jobRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.vectorSearchService = vectorSearchService;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.skillExtractionService = skillExtractionService;
        this.ruleBasedEnrichmentService = ruleBasedEnrichmentService;
        this.gapAnalysisService = gapAnalysisService;
        this.skillEquivalenceService = skillEquivalenceService;
        this.transferabilityService = transferabilityService;
        this.explanationService = explanationService;
        this.rerankingDiagnosticService = rerankingDiagnosticService;
    }

    @Transactional(readOnly = true)
    public VectorFirstCompatibilityResponse analyze(Long profileId, Integer limit) {
        if (profileId == null || profileId <= 0) {
            throw new IllegalArgumentException("Profile id must be positive");
        }

        int normalizedLimit = normalizeLimit(limit);
        CandidateProfile profile = candidateProfileRepository.findById(profileId)
                .orElseThrow(() -> new CompatibilityAnalysisException(
                        HttpStatus.NOT_FOUND,
                        "Candidate profile not found: " + profileId
                ));

        validateReadyBgeM3Embeddings(profileId);

        EmbeddingVectorSearchResponse vectorResponse = vectorSearchService.searchJobsForProfile(
                profileId,
                normalizedLimit,
                EMBEDDING_MODEL
        );
        validateVectorResponse(vectorResponse);

        List<EmbeddingVectorSearchResult> vectorResults = vectorResponse.results();
        if (vectorResults == null || vectorResults.isEmpty()) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "Vector search returned no READY JOB embeddings for BAAI/bge-m3."
            );
        }

        Map<Long, Job> jobsById = jobRepository.findAllById(vectorResults.stream()
                        .map(EmbeddingVectorSearchResult::jobId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));

        SkillCatalog catalog = skillExtractionService.loadCatalog();
        String profileText = embeddingTextBuilder.buildForCandidateProfile(profile);
        ExtractedSkills profileSkills = skillExtractionService.extractSkills(profileText, catalog);
        RuleBasedEnrichmentService.EnrichedDocument profileEnriched =
                ruleBasedEnrichmentService.enrichCandidate(profileText, profileSkills);

        List<VectorFirstCompatibilityResult> results = new ArrayList<>();
        int vectorRank = 1;
        for (EmbeddingVectorSearchResult vectorResult : vectorResults) {
            Job job = jobsById.get(vectorResult.jobId());
            if (job == null) {
                throw new CompatibilityAnalysisException(
                        HttpStatus.CONFLICT,
                        "Vector result references a missing job row: " + vectorResult.jobId()
                );
            }
            results.add(analyzeJob(
                    job,
                    vectorResult,
                    vectorRank,
                    profileText,
                    profileSkills,
                    profileEnriched,
                    catalog
            ));
            vectorRank++;
        }

        List<VectorFirstCompatibilityResult> diagnosticsWithSuggestedRanks =
                rerankingDiagnosticService.assignSuggestedRanks(results);

        return new VectorFirstCompatibilityResponse(
                profileId,
                EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS,
                new VectorFirstCompatibilityResponse.Retrieval(normalizedLimit, STRATEGY),
                diagnosticsWithSuggestedRanks
        );
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private VectorFirstCompatibilityResult analyzeJob(
            Job job,
            EmbeddingVectorSearchResult vectorResult,
            int vectorRank,
            String profileText,
            ExtractedSkills profileSkills,
            RuleBasedEnrichmentService.EnrichedDocument profileEnriched,
            SkillCatalog catalog
    ) {
        String jobText = embeddingTextBuilder.buildForJob(job);
        ExtractedSkills jobSkills = skillExtractionService.extractSkills(jobText, catalog);
        RuleBasedEnrichmentService.EnrichedDocument jobEnriched =
                ruleBasedEnrichmentService.enrichJob(jobText, jobSkills);

        GapAnalysis gapAnalysis = gapAnalysisService.analyze(profileText, profileSkills, job, catalog);
        List<TransferableSkill> transferableSkills = transferabilityService.findTransferableSkills(
                transferSourceSignals(profileSkills, profileEnriched),
                transferTargetSignals(gapAnalysis, jobEnriched)
        );
        List<SkillEquivalenceSignal> skillEquivalenceSignals = skillEquivalenceService.findSignals(
                gapAnalysis.candidateSkills(),
                skillEquivalenceTargetSignals(gapAnalysis, jobEnriched)
        );
        String role = detectedRole(job, jobText, jobEnriched);
        String seniority = detectedSeniority(job, jobText, jobEnriched);
        CompatibilitySignalContext signalContext = new CompatibilitySignalContext(
                role,
                seniority,
                profileSeniority(profileEnriched, profileText),
                profileRole(profileEnriched, profileText)
        );
        CompatibilityExplanation explanation = explanationService.explain(
                profileText,
                vectorResult.similarity(),
                gapAnalysis,
                transferableSkills,
                profileEnriched,
                jobEnriched,
                signalContext
        );

        VectorFirstCompatibilityResult result = new VectorFirstCompatibilityResult(
                job.getId(),
                coalesce(job.getTitle(), "Untitled"),
                coalesce(job.getCompany(), "Unknown"),
                vectorRank,
                vectorResult.similarity(),
                vectorRank,
                role,
                seniority,
                explanation.compatibilityCategory(),
                explanation.evidenceLevel(),
                gapAnalysis.matchedSkills(),
                gapAnalysis.missingCriticalSkills(),
                gapAnalysis.missingSecondarySkills(),
                transferableSkills,
                explanation.roadmapSuggestions(),
                explanation.explanation(),
                explanation.confidence()
        ).withSkillEquivalenceSignals(skillEquivalenceSignals);
        return result.withDiagnostic(rerankingDiagnosticService.evaluate(result, signalContext));
    }

    private void validateReadyBgeM3Embeddings(Long profileId) {
        boolean hasProfileForModel = vectorSearchRepository.hasReadyProfileEmbeddingForModel(profileId, EMBEDDING_MODEL);
        boolean hasProfileWithExpectedDimensions = vectorSearchRepository.hasReadyProfileEmbedding(
                profileId,
                EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS
        );
        if (!hasProfileForModel) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "No READY PROFILE embedding found for BAAI/bge-m3 and profileId=" + profileId + "."
            );
        }
        if (!hasProfileWithExpectedDimensions) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "READY PROFILE embedding for BAAI/bge-m3 has unsupported vector dimensions; expected 1024."
            );
        }

        boolean hasJobsForModel = vectorSearchRepository.hasReadyJobEmbeddingForModel(EMBEDDING_MODEL);
        boolean hasJobsWithExpectedDimensions = vectorSearchRepository.hasReadyJobEmbedding(
                EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS
        );
        if (!hasJobsForModel) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "No READY JOB embeddings found for BAAI/bge-m3."
            );
        }
        if (!hasJobsWithExpectedDimensions) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "READY JOB embeddings for BAAI/bge-m3 have unsupported vector dimensions; expected 1024."
            );
        }
    }

    private static void validateVectorResponse(EmbeddingVectorSearchResponse vectorResponse) {
        if (vectorResponse == null) {
            throw new CompatibilityAnalysisException(HttpStatus.CONFLICT, "Vector search returned no response.");
        }
        if (!EMBEDDING_MODEL.equals(vectorResponse.embeddingModel())) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "Vector search returned an unexpected embedding model: " + vectorResponse.embeddingModel()
            );
        }
        if (vectorResponse.embeddingDimensions() != EMBEDDING_DIMENSIONS) {
            throw new CompatibilityAnalysisException(
                    HttpStatus.CONFLICT,
                    "Vector search returned unsupported vector dimensions; expected 1024."
            );
        }
    }

    private static List<String> transferSourceSignals(
            ExtractedSkills profileSkills,
            RuleBasedEnrichmentService.EnrichedDocument profileEnriched
    ) {
        Set<String> out = new LinkedHashSet<>();
        if (profileSkills != null && profileSkills.skillIdToName() != null) {
            out.addAll(profileSkills.skillIdToName().values());
        }
        if (profileEnriched != null) {
            if (profileEnriched.inferred() != null) {
                profileEnriched.inferred().forEach(item -> out.add(item.label()));
            }
            if (profileEnriched.categories() != null) {
                profileEnriched.categories().stream()
                        .map(RuleBasedEnrichmentService::displayCategory)
                        .filter(Objects::nonNull)
                        .forEach(out::add);
            }
        }
        return out.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static List<String> transferTargetSignals(
            GapAnalysis gapAnalysis,
            RuleBasedEnrichmentService.EnrichedDocument jobEnriched
    ) {
        Set<String> out = new LinkedHashSet<>();
        if (gapAnalysis != null) {
            out.addAll(gapAnalysis.missingCriticalSkills());
            out.addAll(gapAnalysis.missingSecondarySkills());
        }
        if (jobEnriched != null) {
            if (jobEnriched.inferred() != null) {
                jobEnriched.inferred().forEach(item -> out.add(item.label()));
            }
            if (jobEnriched.categories() != null) {
                jobEnriched.categories().stream()
                        .map(RuleBasedEnrichmentService::displayCategory)
                        .filter(Objects::nonNull)
                        .forEach(out::add);
            }
        }
        return out.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static List<String> missingSkills(GapAnalysis gapAnalysis) {
        if (gapAnalysis == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        if (gapAnalysis.missingCriticalSkills() != null) {
            out.addAll(gapAnalysis.missingCriticalSkills());
        }
        if (gapAnalysis.missingSecondarySkills() != null) {
            out.addAll(gapAnalysis.missingSecondarySkills());
        }
        return out.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static List<String> skillEquivalenceTargetSignals(
            GapAnalysis gapAnalysis,
            RuleBasedEnrichmentService.EnrichedDocument jobEnriched
    ) {
        Set<String> out = new LinkedHashSet<>(missingSkills(gapAnalysis));
        if (jobEnriched != null) {
            if (jobEnriched.inferred() != null) {
                jobEnriched.inferred().forEach(item -> out.add(item.label()));
            }
            if (jobEnriched.categories() != null) {
                jobEnriched.categories().stream()
                        .map(RuleBasedEnrichmentService::displayCategory)
                        .filter(Objects::nonNull)
                        .forEach(out::add);
            }
        }
        return out.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    static String detectedRole(Job job, String jobText, RuleBasedEnrichmentService.EnrichedDocument jobEnriched) {
        String title = SkillExtractionService.normalizeText(job == null ? null : job.getTitle());
        String text = SkillExtractionService.normalizeText(jobText);
        String combined = joinNonBlank(title, text);

        if (containsAny(combined, "identity and access management", "identity access management", "iam engineer", " iam ", "access management", "sailpoint", "okta", "cyberark", "oauth", "oidc", "saml")) {
            return "IAM";
        }
        if (containsAny(combined, "security operations", "security ops", "soc analyst", "incident response", "information security", "data protection")) {
            return "SECURITY_OPS";
        }
        if (containsAny(combined, "application support", "soporte a aplicaciones", "soporte de aplicaciones", "soporte de apps", "app support", "mesa de ayuda apps")) {
            return "APP_SUPPORT";
        }
        if (containsAny(combined, ".net", "dotnet", "asp.net", "c#")) {
            if (containsAny(combined, "fullstack", "full stack", "javascript", "react", "angular", "frontend", "front end")) {
                return "DOTNET_FULLSTACK";
            }
            return "DOTNET_BACKEND";
        }
        if (isDatabaseRole(title, combined)) {
            return "DATABASE";
        }
        if (containsAny(title, "full stack", "fullstack")) {
            return "FULL_STACK";
        }
        if (containsAny(title, "backend", "back end", "back-end")) {
            return "BACKEND";
        }
        if (containsAny(title, "frontend", "front end", "front-end")) {
            return "FRONTEND";
        }
        if (containsAny(title, "devops", "sre")) {
            return "DEVOPS";
        }
        if (containsAny(title, "cloud") || (containsAny(combined, "cloud") && !isDatabaseRole(title, combined))) {
            return "CLOUD";
        }
        if (containsAny(title, "data analyst", "data engineer", "analytics")) {
            return "DATA";
        }
        if (containsAny(title, "qa", "quality")) {
            return "QA";
        }
        if (containsAny(title, "support", "soporte", "help desk", "service desk")) {
            return "IT_SUPPORT";
        }

        if (jobEnriched == null || jobEnriched.categories() == null || jobEnriched.categories().isEmpty()) {
            return "UNKNOWN";
        }
        Set<RuleBasedEnrichmentService.Category> categories = jobEnriched.categories();
        if (categories.contains(RuleBasedEnrichmentService.Category.BACKEND)
                && categories.contains(RuleBasedEnrichmentService.Category.FRONTEND)) {
            return "FULL_STACK";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.BACKEND)) {
            return "BACKEND";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.FRONTEND)) {
            return "FRONTEND";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.DEVOPS)) {
            return "DEVOPS";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.CLOUD)) {
            return "CLOUD";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.DATA)) {
            return "DATA";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.QA)) {
            return "QA";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.IT_SUPPORT)) {
            return "IT_SUPPORT";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.TECH_SALES)) {
            return "TECH_SALES";
        }
        if (categories.contains(RuleBasedEnrichmentService.Category.CONSULTING)) {
            return "CONSULTING";
        }
        return "UNKNOWN";
    }

    static String detectedSeniority(Job job, String jobText, RuleBasedEnrichmentService.EnrichedDocument jobEnriched) {
        String title = SkillExtractionService.normalizeText(job == null ? null : job.getTitle());
        String text = SkillExtractionService.normalizeText(jobText);
        String combined = joinNonBlank(title, firstWords(text, 180));
        if (containsAny(combined, "trainee", "intern", "internship", "pasante", "pasantia", "entry level", "entry-level")) {
            return "TRAINEE";
        }
        if (containsAny(title, "junior", "jr")) {
            return "JUNIOR";
        }
        if (containsAny(title, "semi senior", "semisenior", "ssr", "mid")) {
            return "MID";
        }
        if (containsAny(title, "tech lead", "team lead", "principal", "staff")) {
            return "LEAD";
        }
        if (containsAny(title, "senior", "sr")) {
            return "SENIOR";
        }
        if (containsAny(combined, "tech lead", "team lead", "principal", "staff")) {
            return "LEAD";
        }
        if (containsExperienceYears(combined, 5) || containsAny(combined, "senior", "sr")) {
            return "SENIOR";
        }
        if (containsExperienceYears(combined, 3)) {
            return "MID";
        }
        if (containsAny(combined, "semi senior", "semisenior", "ssr", "mid")) {
            return "MID";
        }
        if (containsAny(combined, "junior", "jr")) {
            return "JUNIOR";
        }
        if (jobEnriched != null && jobEnriched.seniority() != null) {
            return jobEnriched.seniority().name();
        }
        return "UNKNOWN";
    }

    private static String profileSeniority(RuleBasedEnrichmentService.EnrichedDocument profileEnriched, String profileText) {
        if (profileEnriched != null && profileEnriched.seniority() != null) {
            return profileEnriched.seniority().name();
        }
        String text = SkillExtractionService.normalizeText(profileText);
        if (containsAny(text, "trainee", "junior", "jr", "entry level")) {
            return containsAny(text, "trainee") ? "TRAINEE" : "JUNIOR";
        }
        return "UNKNOWN";
    }

    static String profileRole(RuleBasedEnrichmentService.EnrichedDocument profileEnriched, String profileText) {
        String text = SkillExtractionService.normalizeText(profileText);
        if (containsAny(text, "identity and access management", "iam", "oauth", "oidc", "saml")) {
            return "IAM";
        }
        if (containsAny(text, "security operations", "soc analyst", "incident response", "information security")) {
            return "SECURITY_OPS";
        }
        if (containsAny(text, "application support", "soporte a aplicaciones", "app support")) {
            return "APP_SUPPORT";
        }
        if (containsAny(text, "help desk", "service desk", "technical support", "it support", "soporte tecnico", "it analyst")) {
            return "IT_SUPPORT";
        }
        if (containsAny(text, "data analyst", "bi analyst", "business intelligence", "power bi", "dashboards", "reporting")) {
            return "DATA";
        }
        if (hasBackendProfileCore(text)) {
            return "BACKEND";
        }
        if (containsAny(text, "cloud", "devops", "kubernetes", "ci/cd", "aws")) {
            return containsAny(text, "backend") ? "BACKEND" : "CLOUD";
        }
        if (containsAny(text, "frontend", "front end", "react", "angular")) {
            return "FRONTEND";
        }
        if (hasQaProfileCore(text)) {
            return "QA";
        }
        if (profileEnriched != null && profileEnriched.categories() != null) {
            Set<RuleBasedEnrichmentService.Category> categories = profileEnriched.categories();
            if (categories.contains(RuleBasedEnrichmentService.Category.DATA)) {
                return "DATA";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.IT_SUPPORT)) {
                return "IT_SUPPORT";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.CLOUD)) {
                return "CLOUD";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.DEVOPS)) {
                return "DEVOPS";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.BACKEND)) {
                return "BACKEND";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.FRONTEND)) {
                return "FRONTEND";
            }
            if (categories.contains(RuleBasedEnrichmentService.Category.QA)) {
                return "QA";
            }
        }
        return "BACKEND";
    }

    private static boolean hasBackendProfileCore(String text) {
        return containsAny(
                text,
                "backend",
                "back end",
                "back-end",
                "spring boot",
                "java spring",
                "rest apis",
                "microservices",
                "crud services"
        );
    }

    private static boolean hasQaProfileCore(String text) {
        return containsAny(
                text,
                "qa",
                "quality assurance",
                "qa automation",
                "test automation",
                "automation testing",
                "manual testing",
                "tester"
        );
    }

    private static boolean isDatabaseRole(String title, String combined) {
        if (containsAny(title, "database", "base de datos", "db developer", "dba", "data base")) {
            return true;
        }
        return containsAny(combined, "postgresql", "postgre sql", "oracle", "sql server", "pl/sql", "pl sql", "database developer", "desarrollador de base de datos")
                && !containsAny(combined, "application support", "soporte a aplicaciones", "iam");
    }

    private static boolean containsExperienceYears(String normalizedText, int minimumYears) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{1,2})\\s*(\\+)?\\s*(anos|anios|years|year)\\b")
                .matcher(normalizedText);
        while (matcher.find()) {
            try {
                if (Integer.parseInt(matcher.group(1)) >= minimumYears) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static String firstWords(String value, int maxWords) {
        if (value == null || value.isBlank() || maxWords <= 0) {
            return "";
        }
        String[] parts = value.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && i < maxWords; i++) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(parts[i]);
        }
        return out.toString();
    }

    private static boolean containsAny(String normalizedText, String... phrases) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        String haystack = " " + normalizedText + " ";
        for (String phrase : phrases) {
            String needle = " " + SkillExtractionService.normalizeText(phrase) + " ";
            if (!needle.isBlank() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String joinNonBlank(String... values) {
        StringBuilder out = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(value);
        }
        return out.toString();
    }
}
