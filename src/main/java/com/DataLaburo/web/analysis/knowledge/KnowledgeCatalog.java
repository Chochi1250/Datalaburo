package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.TransferStrength;
import com.DataLaburo.web.analysis.evidence.ProfessionalDomain;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;

import java.util.List;

public record KnowledgeCatalog(
        int version,
        List<RoleFamilyDefinition> roleFamilies,
        List<TechnologyDefinition> technologies,
        List<TransferRule> transfers,
        List<SeniorityRule> seniorityRules,
        List<String> explicitOutOfScopeRoleAliases,
        FallbackCopy fallbacks
) {
    public KnowledgeCatalog {
        roleFamilies = safe(roleFamilies);
        technologies = safe(technologies);
        transfers = safe(transfers);
        seniorityRules = safe(seniorityRules);
        explicitOutOfScopeRoleAliases = safe(explicitOutOfScopeRoleAliases);
    }

    public record RoleFamilyDefinition(
            String id,
            String label,
            List<String> aliases,
            List<ProfessionalDomain> evidenceDomains,
            List<String> coreTechnologyRefs,
            List<String> seniorityEvidenceTechnologyRefs,
            String alignedCopy,
            String transitionCopy,
            String limitedContextCopy,
            List<String> favorableSignals,
            List<String> strongEvidenceSignals,
            List<String> supportingEvidenceSignals,
            List<String> insufficientEvidenceSignals,
            List<String> frequentGapExplanations,
            List<String> concreteActions,
            List<String> projectEvidenceIdeas,
            List<String> cvIdeas,
            List<String> shortRoadmap
    ) {
        public RoleFamilyDefinition {
            aliases = safe(aliases);
            evidenceDomains = safe(evidenceDomains);
            coreTechnologyRefs = safe(coreTechnologyRefs);
            seniorityEvidenceTechnologyRefs = safe(seniorityEvidenceTechnologyRefs);
            favorableSignals = safe(favorableSignals);
            strongEvidenceSignals = safe(strongEvidenceSignals);
            supportingEvidenceSignals = safe(supportingEvidenceSignals);
            insufficientEvidenceSignals = safe(insufficientEvidenceSignals);
            frequentGapExplanations = safe(frequentGapExplanations);
            concreteActions = safe(concreteActions);
            projectEvidenceIdeas = safe(projectEvidenceIdeas);
            cvIdeas = safe(cvIdeas);
            shortRoadmap = safe(shortRoadmap);
        }
    }

    public record TechnologyDefinition(
            String id,
            String label,
            List<String> matchingSkillRefs,
            List<String> evidenceSkillRefs,
            List<String> relatedTechnologyRefs,
            EvidenceRule evidence,
            GapRule gaps,
            List<ActionDefinition> actions,
            List<String> projectIdeas,
            List<String> cvIdeas,
            List<String> shortRoadmap
    ) {
        public TechnologyDefinition {
            matchingSkillRefs = safe(matchingSkillRefs);
            evidenceSkillRefs = safe(evidenceSkillRefs);
            relatedTechnologyRefs = safe(relatedTechnologyRefs);
            actions = safe(actions);
            projectIdeas = safe(projectIdeas);
            cvIdeas = safe(cvIdeas);
            shortRoadmap = safe(shortRoadmap);
        }
    }

    public record EvidenceRule(
            List<ProfessionalEvidenceType> strongTypes,
            List<ProfessionalEvidenceType> supportingTypes,
            List<ProfessionalEvidenceType> weakTypes,
            String strongCopy,
            String supportingCopy,
            String weakCopy,
            String declaredOnlyAction
    ) {
        public EvidenceRule {
            strongTypes = safe(strongTypes);
            supportingTypes = safe(supportingTypes);
            weakTypes = safe(weakTypes);
        }
    }

    public record GapRule(
            String criticalExplanation,
            String secondaryExplanation,
            List<String> evidenceIdeas
    ) {
        public GapRule {
            evidenceIdeas = safe(evidenceIdeas);
        }
    }

    public record ActionDefinition(String id, String text) {
    }

    public record TransferRule(
            String id,
            String fromRoleRef,
            String toRoleRef,
            List<String> requiredSourceTechnologyRefs,
            Boolean requiresAllSourceTechnologies,
            List<String> targetTechnologyRefs,
            TransferStrength strength,
            List<ProfessionalEvidenceType> requiredEvidenceTypes,
            List<String> transferableConcepts,
            String warning
    ) {
        public TransferRule {
            requiredSourceTechnologyRefs = safe(requiredSourceTechnologyRefs);
            requiresAllSourceTechnologies = Boolean.TRUE.equals(requiresAllSourceTechnologies);
            targetTechnologyRefs = safe(targetTechnologyRefs);
            requiredEvidenceTypes = safe(requiredEvidenceTypes);
            transferableConcepts = safe(transferableConcepts);
        }
    }

    public record SeniorityRule(
            String id,
            List<ProfessionalEvidenceType> favorableEvidenceTypes,
            boolean requiresDomainWorkEvidence,
            String copy
    ) {
        public SeniorityRule {
            favorableEvidenceTypes = safe(favorableEvidenceTypes);
        }
    }

    public record FallbackCopy(
            String unknownRole,
            String declaredOnly,
            String weakJobMetadata,
            String outOfScope
    ) {
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
