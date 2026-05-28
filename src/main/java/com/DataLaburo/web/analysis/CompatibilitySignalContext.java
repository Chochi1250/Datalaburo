package com.DataLaburo.web.analysis;

public record CompatibilitySignalContext(
        String detectedRole,
        String detectedSeniority,
        String profileSeniority,
        String profileRole
) {
    public CompatibilitySignalContext(String detectedRole, String detectedSeniority, String profileSeniority) {
        this(detectedRole, detectedSeniority, profileSeniority, null);
    }

    public static CompatibilitySignalContext empty() {
        return new CompatibilitySignalContext(null, null, null, null);
    }
}
