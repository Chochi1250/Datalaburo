package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;

import java.util.List;

public record KnowledgeResolutionInput(
        String profileRole,
        String opportunityRole,
        String opportunitySeniority,
        List<String> matchedSkills,
        List<String> missingCriticalSkills,
        List<String> missingSecondarySkills,
        List<ProfessionalSkillEvidence> skillEvidence,
        boolean insufficientOpportunityMetadata
) {
    public KnowledgeResolutionInput {
        matchedSkills = safe(matchedSkills);
        missingCriticalSkills = safe(missingCriticalSkills);
        missingSecondarySkills = safe(missingSecondarySkills);
        skillEvidence = safe(skillEvidence);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
