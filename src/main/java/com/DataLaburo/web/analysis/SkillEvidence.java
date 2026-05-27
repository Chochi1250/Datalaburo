package com.DataLaburo.web.analysis;

public record SkillEvidence(
        String skillName,
        String matchedAlias,
        String source,
        SkillEvidenceStrength strength,
        String context
) {
}
