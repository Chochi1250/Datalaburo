package com.DataLaburo.web.analysis.evidence;

import java.util.Objects;

public record SeniorityByDomain(
        ProfessionalDomain domain,
        String seniority,
        ProfessionalEvidenceType evidenceType,
        ProfessionalEvidenceStrength confidence,
        String reason
) {
    public SeniorityByDomain {
        domain = domain == null ? ProfessionalDomain.UNKNOWN : domain;
        seniority = Objects.requireNonNullElse(seniority, "UNKNOWN").trim();
        evidenceType = evidenceType == null ? ProfessionalEvidenceType.MISSING : evidenceType;
        confidence = confidence == null ? ProfessionalEvidenceStrength.NONE : confidence;
        reason = Objects.requireNonNullElse(reason, "").trim();
    }
}
