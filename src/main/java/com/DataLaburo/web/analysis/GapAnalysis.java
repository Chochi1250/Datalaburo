package com.DataLaburo.web.analysis;

import java.util.List;

public record GapAnalysis(
        List<String> matchedSkills,
        List<String> missingCriticalSkills,
        List<String> missingSecondarySkills,
        List<String> candidateSkills,
        List<String> criticalSkills,
        List<String> secondarySkills,
        List<SkillEvidence> criticalEvidence,
        List<SkillEvidence> secondaryEvidence
) {
    public GapAnalysis(
            List<String> matchedSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<String> candidateSkills,
            List<String> criticalSkills,
            List<String> secondarySkills
    ) {
        this(
                matchedSkills,
                missingCriticalSkills,
                missingSecondarySkills,
                candidateSkills,
                criticalSkills,
                secondarySkills,
                List.of(),
                List.of()
        );
    }

    public int directMatchCount() {
        return matchedSkills == null ? 0 : matchedSkills.size();
    }

    public int criticalGapCount() {
        return missingCriticalSkills == null ? 0 : missingCriticalSkills.size();
    }

    public int secondaryGapCount() {
        return missingSecondarySkills == null ? 0 : missingSecondarySkills.size();
    }

    public int totalGapCount() {
        return criticalGapCount() + secondaryGapCount();
    }
}
