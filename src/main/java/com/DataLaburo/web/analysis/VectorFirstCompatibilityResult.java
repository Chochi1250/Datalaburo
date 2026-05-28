package com.DataLaburo.web.analysis;

import java.util.List;

public record VectorFirstCompatibilityResult(
        Long jobId,
        String title,
        String company,
        int vectorRank,
        double vectorSimilarity,
        int analysisRank,
        String detectedRole,
        String detectedSeniority,
        CompatibilityCategory compatibilityCategory,
        EvidenceLevel evidenceLevel,
        List<String> matchedSkills,
        List<String> missingCriticalSkills,
        List<String> missingSecondarySkills,
        List<TransferableSkill> transferableSkills,
        List<String> roadmapSuggestions,
        String explanation,
        CompatibilityConfidence confidence,
        CompatibilityBucket compatibilityBucket,
        Integer suggestedRerankRank,
        Integer suggestedRankDelta,
        List<String> rerankReasons,
        List<String> rerankWarnings,
        List<RerankSignal> rerankSignals
) {
    public VectorFirstCompatibilityResult(
            Long jobId,
            String title,
            String company,
            int vectorRank,
            double vectorSimilarity,
            int analysisRank,
            String detectedRole,
            String detectedSeniority,
            CompatibilityCategory compatibilityCategory,
            EvidenceLevel evidenceLevel,
            List<String> matchedSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<TransferableSkill> transferableSkills,
            List<String> roadmapSuggestions,
            String explanation,
            CompatibilityConfidence confidence
    ) {
        this(
                jobId,
                title,
                company,
                vectorRank,
                vectorSimilarity,
                analysisRank,
                detectedRole,
                detectedSeniority,
                compatibilityCategory,
                evidenceLevel,
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                transferableSkills,
                roadmapSuggestions,
                explanation,
                confidence,
                null,
                vectorRank,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public VectorFirstCompatibilityResult withDiagnostic(RerankingDiagnostic diagnostic) {
        return new VectorFirstCompatibilityResult(
                jobId,
                title,
                company,
                vectorRank,
                vectorSimilarity,
                analysisRank,
                detectedRole,
                detectedSeniority,
                compatibilityCategory,
                evidenceLevel,
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                transferableSkills,
                roadmapSuggestions,
                explanation,
                confidence,
                diagnostic == null ? compatibilityBucket : diagnostic.compatibilityBucket(),
                suggestedRerankRank,
                suggestedRankDelta,
                diagnostic == null ? rerankReasons : diagnostic.rerankReasons(),
                diagnostic == null ? rerankWarnings : diagnostic.rerankWarnings(),
                diagnostic == null ? rerankSignals : diagnostic.rerankSignals()
        );
    }

    public VectorFirstCompatibilityResult withSuggestedRerankRank(
            Integer nextSuggestedRerankRank,
            Integer nextSuggestedRankDelta,
            List<String> nextRerankReasons
    ) {
        return new VectorFirstCompatibilityResult(
                jobId,
                title,
                company,
                vectorRank,
                vectorSimilarity,
                analysisRank,
                detectedRole,
                detectedSeniority,
                compatibilityCategory,
                evidenceLevel,
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                transferableSkills,
                roadmapSuggestions,
                explanation,
                confidence,
                compatibilityBucket,
                nextSuggestedRerankRank,
                nextSuggestedRankDelta,
                nextRerankReasons,
                rerankWarnings,
                rerankSignals
        );
    }
}
