package com.DataLaburo.web.analysis.evidence;

import java.util.List;
import java.util.Objects;

public record ProfessionalSkillEvidence(
        String skillName,
        ProfessionalEvidenceType evidenceType,
        ProfessionalEvidenceStrength strength,
        ProfessionalDomain domain,
        ProfessionalEvidenceSource source,
        String sourceLabel,
        String context,
        List<String> warnings
) {
    public ProfessionalSkillEvidence {
        skillName = Objects.requireNonNullElse(skillName, "").trim();
        evidenceType = evidenceType == null ? ProfessionalEvidenceType.MISSING : evidenceType;
        strength = strength == null ? ProfessionalEvidenceStrength.NONE : strength;
        domain = domain == null ? ProfessionalDomain.UNKNOWN : domain;
        source = source == null ? ProfessionalEvidenceSource.UNKNOWN : source;
        sourceLabel = Objects.requireNonNullElse(sourceLabel, "").trim();
        context = Objects.requireNonNullElse(context, "").trim();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isDirectWorkEvidence() {
        return evidenceType == ProfessionalEvidenceType.WORK_EXPERIENCE;
    }

    public boolean isStrongOrMedium() {
        return strength.atLeast(ProfessionalEvidenceStrength.MEDIUM);
    }
}
