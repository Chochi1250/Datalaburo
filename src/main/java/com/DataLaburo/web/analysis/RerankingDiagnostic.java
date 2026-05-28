package com.DataLaburo.web.analysis;

import java.util.List;

public record RerankingDiagnostic(
        CompatibilityBucket compatibilityBucket,
        List<String> rerankReasons,
        List<String> rerankWarnings,
        List<RerankSignal> rerankSignals
) {
}
