package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeCatalogResolver {
    private static final int MAX_ACTIONS = 3;

    private final KnowledgeCatalog catalog;
    private final Map<String, KnowledgeCatalog.RoleFamilyDefinition> roleByAlias;
    private final Map<String, KnowledgeCatalog.TechnologyDefinition> technologyById;
    private final Map<String, KnowledgeCatalog.TechnologyDefinition> technologyByMatchingSkill;
    private final Map<String, KnowledgeCatalog.SeniorityRule> seniorityById;

    @Autowired
    public KnowledgeCatalogResolver(KnowledgeCatalogLoader loader) {
        this(loader.catalog());
    }

    KnowledgeCatalogResolver(KnowledgeCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.roleByAlias = indexRoles(catalog.roleFamilies());
        this.technologyById = indexTechnologies(catalog.technologies());
        this.technologyByMatchingSkill = indexMatchingSkills(catalog.technologies());
        this.seniorityById = indexSeniority(catalog.seniorityRules());
    }

    public OpportunityKnowledgeEnrichment resolve(KnowledgeResolutionInput input) {
        Objects.requireNonNull(input, "input must not be null");

        KnowledgeCatalog.RoleFamilyDefinition profileRole = resolveRole(input.profileRole());
        KnowledgeCatalog.RoleFamilyDefinition opportunityRole = resolveRole(input.opportunityRole());
        boolean limitedContext = input.insufficientOpportunityMetadata() || opportunityRole == null;

        List<String> unresolvedSignals = new ArrayList<>();
        Map<String, TechnologySignals> matched = resolveTechnologySignals(input.matchedSkills(), unresolvedSignals);
        Map<String, TechnologySignals> critical = resolveTechnologySignals(input.missingCriticalSkills(), unresolvedSignals);
        Map<String, TechnologySignals> secondary = resolveTechnologySignals(input.missingSecondarySkills(), unresolvedSignals);
        critical.keySet().forEach(secondary::remove);

        Map<String, ProfessionalSkillEvidence> evidenceBySkill = indexEvidence(input.skillEvidence());
        List<OpportunityKnowledgeEnrichment.Strength> strengths = buildStrengths(matched, evidenceBySkill);
        List<OpportunityKnowledgeEnrichment.Gap> gaps = buildGaps(critical, secondary);
        List<OpportunityKnowledgeEnrichment.Transfer> transfers = limitedContext
                ? List.of()
                : buildTransfers(profileRole, opportunityRole, input.skillEvidence());
        OpportunityKnowledgeEnrichment.SeniorityGuidance seniority = buildSeniorityGuidance(
                input.opportunitySeniority(),
                opportunityRole,
                input.skillEvidence()
        );
        List<OpportunityKnowledgeEnrichment.Action> actions = buildActions(
                critical,
                secondary,
                strengths
        );

        List<String> warnings = new ArrayList<>();
        if (input.insufficientOpportunityMetadata()) {
            warnings.add(catalog.fallbacks().weakJobMetadata());
        }
        if (opportunityRole == null) {
            warnings.add(catalog.fallbacks().unknownRole());
        }

        return new OpportunityKnowledgeEnrichment(
                limitedContext
                        ? OpportunityKnowledgeEnrichment.ContextLevel.LIMITED
                        : OpportunityKnowledgeEnrichment.ContextLevel.SUPPORTED,
                opportunityRole == null
                        ? null
                        : new OpportunityKnowledgeEnrichment.RoleFamily(opportunityRole.id(), opportunityRole.label()),
                roleExplanation(profileRole, opportunityRole, input.insufficientOpportunityMetadata()),
                strengths,
                gaps,
                transfers,
                seniority,
                actions,
                distinct(unresolvedSignals),
                distinct(warnings)
        );
    }

    private String roleExplanation(
            KnowledgeCatalog.RoleFamilyDefinition profileRole,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            boolean insufficientMetadata
    ) {
        if (insufficientMetadata) {
            return catalog.fallbacks().weakJobMetadata();
        }
        if (opportunityRole == null) {
            return catalog.fallbacks().unknownRole();
        }
        return profileRole != null && profileRole.id().equals(opportunityRole.id())
                ? opportunityRole.alignedCopy()
                : opportunityRole.transitionCopy();
    }

    private List<OpportunityKnowledgeEnrichment.Strength> buildStrengths(
            Map<String, TechnologySignals> matched,
            Map<String, ProfessionalSkillEvidence> evidenceBySkill
    ) {
        List<OpportunityKnowledgeEnrichment.Strength> out = new ArrayList<>();
        for (TechnologySignals signals : matched.values()) {
            KnowledgeCatalog.TechnologyDefinition technology = signals.technology();
            ProfessionalSkillEvidence evidence = strongestEvidence(technology, evidenceBySkill);
            OpportunityKnowledgeEnrichment.EvidenceAssessment assessment = assessment(technology, evidence);
            out.add(new OpportunityKnowledgeEnrichment.Strength(
                    technology.id(),
                    technology.label(),
                    List.copyOf(signals.sourceSignals()),
                    evidence == null ? null : evidence.evidenceType(),
                    assessment,
                    evidenceCopy(technology, assessment)
            ));
        }
        return List.copyOf(out);
    }

    private static List<OpportunityKnowledgeEnrichment.Gap> buildGaps(
            Map<String, TechnologySignals> critical,
            Map<String, TechnologySignals> secondary
    ) {
        List<OpportunityKnowledgeEnrichment.Gap> out = new ArrayList<>();
        critical.values().forEach(signals -> out.add(toGap(
                signals,
                OpportunityKnowledgeEnrichment.GapSeverity.CRITICAL
        )));
        secondary.values().forEach(signals -> out.add(toGap(
                signals,
                OpportunityKnowledgeEnrichment.GapSeverity.SECONDARY
        )));
        return List.copyOf(out);
    }

    private static OpportunityKnowledgeEnrichment.Gap toGap(
            TechnologySignals signals,
            OpportunityKnowledgeEnrichment.GapSeverity severity
    ) {
        KnowledgeCatalog.TechnologyDefinition technology = signals.technology();
        String explanation = severity == OpportunityKnowledgeEnrichment.GapSeverity.CRITICAL
                ? technology.gaps().criticalExplanation()
                : technology.gaps().secondaryExplanation();
        return new OpportunityKnowledgeEnrichment.Gap(
                technology.id(),
                technology.label(),
                List.copyOf(signals.sourceSignals()),
                severity,
                explanation,
                technology.gaps().evidenceIdeas()
        );
    }

    private List<OpportunityKnowledgeEnrichment.Transfer> buildTransfers(
            KnowledgeCatalog.RoleFamilyDefinition profileRole,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            List<ProfessionalSkillEvidence> evidence
    ) {
        if (profileRole == null || opportunityRole == null || profileRole.id().equals(opportunityRole.id())) {
            return List.of();
        }
        List<OpportunityKnowledgeEnrichment.Transfer> out = new ArrayList<>();
        for (KnowledgeCatalog.TransferRule rule : catalog.transfers()) {
            if (!rule.fromRoleRef().equals(profileRole.id()) || !rule.toRoleRef().equals(opportunityRole.id())) {
                continue;
            }
            if (!hasRequiredSourceEvidence(profileRole, evidence, rule.requiredEvidenceTypes())) {
                continue;
            }
            List<String> targetTechnologies = rule.targetTechnologyRefs().stream()
                    .map(technologyById::get)
                    .filter(Objects::nonNull)
                    .map(KnowledgeCatalog.TechnologyDefinition::label)
                    .toList();
            out.add(new OpportunityKnowledgeEnrichment.Transfer(
                    rule.id(),
                    rule.fromRoleRef(),
                    rule.toRoleRef(),
                    rule.strength(),
                    targetTechnologies,
                    rule.transferableConcepts(),
                    rule.warning()
            ));
        }
        return List.copyOf(out);
    }

    private OpportunityKnowledgeEnrichment.SeniorityGuidance buildSeniorityGuidance(
            String seniorityValue,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            List<ProfessionalSkillEvidence> evidence
    ) {
        String seniorityId = normalizeId(seniorityValue);
        KnowledgeCatalog.SeniorityRule rule = seniorityById.get(seniorityId);
        if (rule == null) {
            return null;
        }
        boolean domainWorkEvidence = opportunityRole != null && evidence.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item.evidenceType() == ProfessionalEvidenceType.WORK_EXPERIENCE
                        && opportunityRole.evidenceDomains().contains(item.domain()));
        return new OpportunityKnowledgeEnrichment.SeniorityGuidance(
                rule.id(),
                rule.copy(),
                rule.requiresDomainWorkEvidence(),
                domainWorkEvidence
        );
    }

    private List<OpportunityKnowledgeEnrichment.Action> buildActions(
            Map<String, TechnologySignals> critical,
            Map<String, TechnologySignals> secondary,
            List<OpportunityKnowledgeEnrichment.Strength> strengths
    ) {
        Map<String, OpportunityKnowledgeEnrichment.Action> out = new LinkedHashMap<>();
        addGapActions(out, critical, "Brecha critica detectada");
        for (OpportunityKnowledgeEnrichment.Strength strength : strengths) {
            if (strength.evidenceAssessment() != OpportunityKnowledgeEnrichment.EvidenceAssessment.WEAK) {
                continue;
            }
            KnowledgeCatalog.TechnologyDefinition technology = technologyById.get(strength.technologyId());
            String id = "EVIDENCE_" + technology.id();
            out.putIfAbsent(id, new OpportunityKnowledgeEnrichment.Action(
                    id,
                    technology.evidence().declaredOnlyAction(),
                    technology.id(),
                    "Skill declarada o transferible sin evidencia directa"
            ));
        }
        addGapActions(out, secondary, "Brecha secundaria detectada");
        return out.values().stream().limit(MAX_ACTIONS).toList();
    }

    private static void addGapActions(
            Map<String, OpportunityKnowledgeEnrichment.Action> out,
            Map<String, TechnologySignals> gaps,
            String reason
    ) {
        for (TechnologySignals signals : gaps.values()) {
            KnowledgeCatalog.TechnologyDefinition technology = signals.technology();
            for (KnowledgeCatalog.ActionDefinition action : technology.actions()) {
                out.putIfAbsent(action.id(), new OpportunityKnowledgeEnrichment.Action(
                        action.id(),
                        action.text(),
                        technology.id(),
                        reason
                ));
            }
        }
    }

    private static boolean hasRequiredSourceEvidence(
            KnowledgeCatalog.RoleFamilyDefinition sourceRole,
            List<ProfessionalSkillEvidence> evidence,
            List<ProfessionalEvidenceType> requiredTypes
    ) {
        return evidence.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> sourceRole.evidenceDomains().contains(item.domain())
                        && requiredTypes.contains(item.evidenceType()));
    }

    private static ProfessionalSkillEvidence strongestEvidence(
            KnowledgeCatalog.TechnologyDefinition technology,
            Map<String, ProfessionalSkillEvidence> evidenceBySkill
    ) {
        LinkedHashSet<String> refs = new LinkedHashSet<>(technology.matchingSkillRefs());
        refs.addAll(technology.evidenceSkillRefs());
        return refs.stream()
                .map(KnowledgeCatalogValidator::normalize)
                .map(evidenceBySkill::get)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(KnowledgeCatalogResolver::evidencePriority))
                .orElse(null);
    }

    private static int evidencePriority(ProfessionalSkillEvidence evidence) {
        if (evidence == null || evidence.evidenceType() == null) {
            return 0;
        }
        return switch (evidence.evidenceType()) {
            case WORK_EXPERIENCE -> 50;
            case PROJECT -> 40;
            case ACADEMIC -> 30;
            case TRANSFERABLE -> 20;
            case DECLARED_ONLY -> 10;
            case MISSING -> 0;
        };
    }

    private static OpportunityKnowledgeEnrichment.EvidenceAssessment assessment(
            KnowledgeCatalog.TechnologyDefinition technology,
            ProfessionalSkillEvidence evidence
    ) {
        if (evidence == null || evidence.evidenceType() == null) {
            return OpportunityKnowledgeEnrichment.EvidenceAssessment.UNVERIFIED;
        }
        if (technology.evidence().strongTypes().contains(evidence.evidenceType())) {
            return OpportunityKnowledgeEnrichment.EvidenceAssessment.STRONG;
        }
        if (technology.evidence().supportingTypes().contains(evidence.evidenceType())) {
            return OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING;
        }
        if (technology.evidence().weakTypes().contains(evidence.evidenceType())) {
            return OpportunityKnowledgeEnrichment.EvidenceAssessment.WEAK;
        }
        return OpportunityKnowledgeEnrichment.EvidenceAssessment.UNVERIFIED;
    }

    private static String evidenceCopy(
            KnowledgeCatalog.TechnologyDefinition technology,
            OpportunityKnowledgeEnrichment.EvidenceAssessment assessment
    ) {
        return switch (assessment) {
            case STRONG -> technology.evidence().strongCopy();
            case SUPPORTING -> technology.evidence().supportingCopy();
            case WEAK -> technology.evidence().weakCopy();
            case UNVERIFIED -> "La coincidencia tecnica no tiene evidencia profesional clasificada en el perfil.";
        };
    }

    private Map<String, TechnologySignals> resolveTechnologySignals(
            List<String> signals,
            List<String> unresolvedSignals
    ) {
        Map<String, TechnologySignals> out = new LinkedHashMap<>();
        for (String signal : signals) {
            if (signal == null || signal.isBlank()) {
                continue;
            }
            KnowledgeCatalog.TechnologyDefinition technology = technologyByMatchingSkill.get(
                    KnowledgeCatalogValidator.normalize(signal)
            );
            if (technology == null) {
                unresolvedSignals.add(signal.trim());
                continue;
            }
            out.computeIfAbsent(technology.id(), ignored -> new TechnologySignals(technology))
                    .sourceSignals()
                    .add(signal.trim());
        }
        return out;
    }

    private KnowledgeCatalog.RoleFamilyDefinition resolveRole(String value) {
        return roleByAlias.get(KnowledgeCatalogValidator.normalize(value));
    }

    private static Map<String, KnowledgeCatalog.RoleFamilyDefinition> indexRoles(
            List<KnowledgeCatalog.RoleFamilyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.RoleFamilyDefinition> out = new LinkedHashMap<>();
        for (KnowledgeCatalog.RoleFamilyDefinition definition : definitions) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            aliases.add(definition.id());
            aliases.add(definition.label());
            aliases.addAll(definition.aliases());
            aliases.forEach(alias -> out.put(KnowledgeCatalogValidator.normalize(alias), definition));
        }
        return Map.copyOf(out);
    }

    private static Map<String, KnowledgeCatalog.TechnologyDefinition> indexTechnologies(
            List<KnowledgeCatalog.TechnologyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.TechnologyDefinition> out = new LinkedHashMap<>();
        definitions.forEach(definition -> out.put(definition.id(), definition));
        return Map.copyOf(out);
    }

    private static Map<String, KnowledgeCatalog.TechnologyDefinition> indexMatchingSkills(
            List<KnowledgeCatalog.TechnologyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.TechnologyDefinition> out = new LinkedHashMap<>();
        for (KnowledgeCatalog.TechnologyDefinition definition : definitions) {
            definition.matchingSkillRefs().forEach(ref -> out.put(
                    KnowledgeCatalogValidator.normalize(ref),
                    definition
            ));
        }
        return Map.copyOf(out);
    }

    private static Map<String, ProfessionalSkillEvidence> indexEvidence(List<ProfessionalSkillEvidence> evidence) {
        Map<String, ProfessionalSkillEvidence> out = new LinkedHashMap<>();
        for (ProfessionalSkillEvidence item : evidence) {
            if (item == null || item.skillName() == null || item.skillName().isBlank()) {
                continue;
            }
            String key = KnowledgeCatalogValidator.normalize(item.skillName());
            ProfessionalSkillEvidence current = out.get(key);
            if (current == null || evidencePriority(item) > evidencePriority(current)) {
                out.put(key, item);
            }
        }
        return out;
    }

    private static Map<String, KnowledgeCatalog.SeniorityRule> indexSeniority(
            List<KnowledgeCatalog.SeniorityRule> rules
    ) {
        Map<String, KnowledgeCatalog.SeniorityRule> out = new LinkedHashMap<>();
        rules.forEach(rule -> out.put(rule.id(), rule));
        return Map.copyOf(out);
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private record TechnologySignals(
            KnowledgeCatalog.TechnologyDefinition technology,
            LinkedHashSet<String> sourceSignals
    ) {
        TechnologySignals(KnowledgeCatalog.TechnologyDefinition technology) {
            this(technology, new LinkedHashSet<>());
        }
    }
}
