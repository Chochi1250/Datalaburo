package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.TransferStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;

import java.util.List;

public record OpportunityKnowledgeEnrichment(
        ContextLevel contextLevel,
        CoverageLevel coverageLevel,
        RoleFamily roleFamily,
        String roleExplanation,
        List<Strength> strengths,
        List<Gap> gaps,
        List<Transfer> transfers,
        SeniorityGuidance seniorityGuidance,
        List<Action> actions,
        List<String> unresolvedSignals,
        List<String> warnings
) {
    public OpportunityKnowledgeEnrichment {
        strengths = safe(strengths);
        gaps = safe(gaps);
        transfers = safe(transfers);
        actions = safe(actions);
        unresolvedSignals = safe(unresolvedSignals);
        warnings = safe(warnings);
    }

    public enum ContextLevel {
        SUPPORTED,
        LIMITED
    }

    public enum CoverageLevel {
        DIRECT_COVERAGE,
        PARTIAL_COVERAGE,
        LOW_CONTEXT,
        OUT_OF_SCOPE
    }

    public enum EvidenceAssessment {
        STRONG,
        SUPPORTING,
        WEAK,
        UNVERIFIED
    }

    public enum GapSeverity {
        CRITICAL,
        SECONDARY
    }

    public record RoleFamily(String id, String label) {
    }

    public record Strength(
            String technologyId,
            String technologyLabel,
            List<String> sourceSkills,
            ProfessionalEvidenceType evidenceType,
            EvidenceAssessment evidenceAssessment,
            String explanation
    ) {
        public Strength {
            sourceSkills = safe(sourceSkills);
        }
    }

    public record Gap(
            String technologyId,
            String technologyLabel,
            List<String> sourceSkills,
            GapSeverity severity,
            String explanation,
            List<String> evidenceIdeas
    ) {
        public Gap {
            sourceSkills = safe(sourceSkills);
            evidenceIdeas = safe(evidenceIdeas);
        }
    }

    public record Transfer(
            String id,
            String fromRoleId,
            String toRoleId,
            TransferStrength strength,
            List<String> sourceTechnologies,
            List<String> targetTechnologies,
            List<String> transferableConcepts,
            String warning
    ) {
        public Transfer {
            sourceTechnologies = safe(sourceTechnologies);
            targetTechnologies = safe(targetTechnologies);
            transferableConcepts = safe(transferableConcepts);
        }
    }

    public record SeniorityGuidance(
            String seniority,
            String copy,
            boolean requiresDomainWorkEvidence,
            boolean domainWorkEvidencePresent
    ) {
    }

    public record Action(String id, String text, String technologyId, String reason) {
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
