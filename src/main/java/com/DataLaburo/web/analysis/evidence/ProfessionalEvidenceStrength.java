package com.DataLaburo.web.analysis.evidence;

public enum ProfessionalEvidenceStrength {
    STRONG,
    MEDIUM,
    WEAK,
    NONE;

    public boolean atLeast(ProfessionalEvidenceStrength other) {
        return score() >= other.score();
    }

    private int score() {
        return switch (this) {
            case STRONG -> 3;
            case MEDIUM -> 2;
            case WEAK -> 1;
            case NONE -> 0;
        };
    }
}
