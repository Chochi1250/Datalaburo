package com.DataLaburo.web.analysis.evidence;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ProfileEvidenceSummary(
        Long profileId,
        List<ProfessionalSkillEvidence> skillEvidence,
        List<SeniorityByDomain> seniorityByDomain,
        List<ProfessionalDomain> strongDomains,
        List<ProfessionalDomain> transitionDomains,
        List<String> declaredOnlySkills,
        List<String> missingSkills
) {
    public ProfileEvidenceSummary {
        skillEvidence = skillEvidence == null ? List.of() : List.copyOf(skillEvidence);
        seniorityByDomain = seniorityByDomain == null ? List.of() : List.copyOf(seniorityByDomain);
        strongDomains = strongDomains == null ? List.of() : List.copyOf(strongDomains);
        transitionDomains = transitionDomains == null ? List.of() : List.copyOf(transitionDomains);
        declaredOnlySkills = declaredOnlySkills == null ? List.of() : List.copyOf(declaredOnlySkills);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
    }

    public Optional<ProfessionalSkillEvidence> strongestEvidenceFor(String skillName) {
        String target = normalizeLabel(skillName);
        return skillEvidence.stream()
                .filter(evidence -> normalizeLabel(evidence.skillName()).equals(target))
                .findFirst();
    }

    public boolean hasEvidence(String skillName, ProfessionalEvidenceType evidenceType) {
        return strongestEvidenceFor(skillName)
                .map(evidence -> evidence.evidenceType() == evidenceType)
                .orElse(false);
    }

    public Optional<SeniorityByDomain> seniorityFor(ProfessionalDomain domain) {
        return seniorityByDomain.stream()
                .filter(seniority -> seniority.domain() == domain)
                .findFirst();
    }

    private static String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
