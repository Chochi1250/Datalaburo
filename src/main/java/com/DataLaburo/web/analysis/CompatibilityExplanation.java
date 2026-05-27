package com.DataLaburo.web.analysis;

import java.util.List;

public record CompatibilityExplanation(
        CompatibilityCategory compatibilityCategory,
        EvidenceLevel evidenceLevel,
        List<String> roadmapSuggestions,
        String explanation,
        CompatibilityConfidence confidence
) {
}
