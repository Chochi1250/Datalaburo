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
        CompatibilityConfidence confidence
) {
}
