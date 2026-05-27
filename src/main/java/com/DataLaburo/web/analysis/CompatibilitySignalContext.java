package com.DataLaburo.web.analysis;

public record CompatibilitySignalContext(
        String detectedRole,
        String detectedSeniority,
        String profileSeniority
) {
    public static CompatibilitySignalContext empty() {
        return new CompatibilitySignalContext(null, null, null);
    }
}
