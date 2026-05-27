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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorFirstCompatibilityServiceTest {
    private final CandidateProfileRepository candidateProfileRepository = mock(CandidateProfileRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final EmbeddingVectorSearchRepository vectorSearchRepository = mock(EmbeddingVectorSearchRepository.class);
    private final EmbeddingVectorSearchService vectorSearchService = mock(EmbeddingVectorSearchService.class);
    private final EmbeddingTextBuilder embeddingTextBuilder = mock(EmbeddingTextBuilder.class);
    private final SkillExtractionService skillExtractionService = mock(SkillExtractionService.class);
    private final RuleBasedEnrichmentService ruleBasedEnrichmentService = mock(RuleBasedEnrichmentService.class);
    private final GapAnalysisService gapAnalysisService = mock(GapAnalysisService.class);
    private final TransferabilityService transferabilityService = mock(TransferabilityService.class);
    private final CompatibilityExplanationService explanationService = mock(CompatibilityExplanationService.class);

    private final VectorFirstCompatibilityService service = new VectorFirstCompatibilityService(
            candidateProfileRepository,
            jobRepository,
            vectorSearchRepository,
            vectorSearchService,
            embeddingTextBuilder,
            skillExtractionService,
            ruleBasedEnrichmentService,
            gapAnalysisService,
            transferabilityService,
            explanationService
    );

    @Test
    void forcesBgeM3AndKeepsVectorRankAsAnalysisRank() {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(1L);
        profile.setName("Candidate");
        profile.setCvText("Java Spring Boot project");

        Job job = new Job();
        job.setId(14L);
        job.setTitle("Backend Engineer");
        job.setCompany("Example");
        job.setSourceUrl("https://example.test/jobs/14");

        SkillExtractionService.SkillCatalog catalog = new SkillExtractionService.SkillCatalog(Map.of(), Map.of());
        SkillExtractionService.ExtractedSkills profileSkills = new SkillExtractionService.ExtractedSkills(Set.of(), Map.of());
        SkillExtractionService.ExtractedSkills jobSkills = new SkillExtractionService.ExtractedSkills(Set.of(), Map.of());
        RuleBasedEnrichmentService.EnrichedDocument profileEnriched = enrichedWithBackend();
        RuleBasedEnrichmentService.EnrichedDocument jobEnriched = enrichedWithBackend();
        GapAnalysis gap = new GapAnalysis(
                List.of("Java"),
                List.of(),
                List.of("Kubernetes"),
                List.of("Java"),
                List.of("Java"),
                List.of("Kubernetes")
        );
        CompatibilityExplanation explanation = new CompatibilityExplanation(
                CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS,
                EvidenceLevel.PROJECT,
                List.of("Profundizar Kubernetes basico"),
                "La oferta esta cerca semanticamente.",
                CompatibilityConfidence.MEDIUM
        );

        when(candidateProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(vectorSearchRepository.hasReadyProfileEmbeddingForModel(1L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL)).thenReturn(true);
        when(vectorSearchRepository.hasReadyProfileEmbedding(1L, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 1024)).thenReturn(true);
        when(vectorSearchRepository.hasReadyJobEmbeddingForModel(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL)).thenReturn(true);
        when(vectorSearchRepository.hasReadyJobEmbedding(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, 1024)).thenReturn(true);
        when(vectorSearchService.searchJobsForProfile(1L, 50, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL))
                .thenReturn(new EmbeddingVectorSearchResponse(
                        1L,
                        DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                        1024,
                        true,
                        "ok",
                        List.of(new EmbeddingVectorSearchResult(
                                14L,
                                100L,
                                0.31d,
                                0.69d,
                                DocumentEmbedding.DEFAULT_EMBEDDING_MODEL,
                                true
                        ))
                ));
        when(jobRepository.findAllById(List.of(14L))).thenReturn(List.of(job));
        when(skillExtractionService.loadCatalog()).thenReturn(catalog);
        when(embeddingTextBuilder.buildForCandidateProfile(profile)).thenReturn("Java Spring Boot project");
        when(embeddingTextBuilder.buildForJob(job)).thenReturn("Backend Engineer Java Kubernetes");
        when(skillExtractionService.extractSkills("Java Spring Boot project", catalog)).thenReturn(profileSkills);
        when(skillExtractionService.extractSkills("Backend Engineer Java Kubernetes", catalog)).thenReturn(jobSkills);
        when(ruleBasedEnrichmentService.enrichCandidate("Java Spring Boot project", profileSkills)).thenReturn(profileEnriched);
        when(ruleBasedEnrichmentService.enrichJob("Backend Engineer Java Kubernetes", jobSkills)).thenReturn(jobEnriched);
        when(gapAnalysisService.analyze("Java Spring Boot project", profileSkills, job, catalog)).thenReturn(gap);
        when(transferabilityService.findTransferableSkills(List.of("Backend"), List.of("Kubernetes", "Backend"))).thenReturn(List.of());
        when(explanationService.explain(
                "Java Spring Boot project",
                0.69d,
                gap,
                List.of(),
                profileEnriched,
                jobEnriched,
                new CompatibilitySignalContext("BACKEND", "MID", "MID")
        ))
                .thenReturn(explanation);

        VectorFirstCompatibilityResponse response = service.analyze(1L, 999);

        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_MODEL, response.embeddingModel());
        assertEquals(50, response.retrieval().limit());
        assertEquals("VECTOR_FIRST_WITH_EXPLANATION", response.retrieval().strategy());
        assertEquals(1, response.results().get(0).vectorRank());
        assertEquals(1, response.results().get(0).analysisRank());
        assertEquals(0.69d, response.results().get(0).vectorSimilarity());
        verify(vectorSearchService).searchJobsForProfile(1L, 50, DocumentEmbedding.DEFAULT_EMBEDDING_MODEL);
    }

    private static RuleBasedEnrichmentService.EnrichedDocument enrichedWithBackend() {
        return new RuleBasedEnrichmentService.EnrichedDocument(
                new SkillExtractionService.ExtractedSkills(Set.of(), Map.of()),
                Set.of(),
                Set.of(RuleBasedEnrichmentService.Category.BACKEND),
                Set.of(),
                null,
                RuleBasedEnrichmentService.Seniority.MID,
                false,
                null,
                Map.of(),
                Map.of()
        );
    }
}
