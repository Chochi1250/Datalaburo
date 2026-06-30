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
    private final Map<String, KnowledgeCatalog.TechnologyDefinition> technologyByEvidenceSkill;
    private final Map<String, KnowledgeCatalog.SeniorityRule> seniorityById;
    private final Set<String> explicitOutOfScopeRoles;

    @Autowired
    public KnowledgeCatalogResolver(KnowledgeCatalogLoader loader) {
        this(loader.catalog());
    }

    KnowledgeCatalogResolver(KnowledgeCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.roleByAlias = indexRoles(catalog.roleFamilies());
        this.technologyById = indexTechnologies(catalog.technologies());
        this.technologyByMatchingSkill = indexMatchingSkills(catalog.technologies());
        this.technologyByEvidenceSkill = indexEvidenceSkills(catalog.technologies());
        this.seniorityById = indexSeniority(catalog.seniorityRules());
        this.explicitOutOfScopeRoles = catalog.explicitOutOfScopeRoleAliases().stream()
                .map(KnowledgeCatalogValidator::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public OpportunityKnowledgeEnrichment resolve(KnowledgeResolutionInput input) {
        Objects.requireNonNull(input, "input must not be null");

        KnowledgeCatalog.RoleFamilyDefinition profileRole = resolveRole(input.profileRole());
        KnowledgeCatalog.RoleFamilyDefinition opportunityRole = resolveRole(input.opportunityRole());
        KnowledgeCatalog.RoleFamilyDefinition secondaryRole = resolveSecondaryRole(
                input.secondaryOpportunityRole(),
                opportunityRole
        );
        if (input.insufficientOpportunityMetadata()) {
            return limitedContextResult(input, opportunityRole, secondaryRole);
        }
        if (explicitOutOfScopeRoles.contains(KnowledgeCatalogValidator.normalize(input.opportunityRole()))) {
            return explicitOutOfScopeResult();
        }
        if (opportunityRole == null) {
            return limitedContextResult(input, null, null);
        }

        List<String> unresolvedSignals = new ArrayList<>();
        Map<String, TechnologySignals> matched = resolveTechnologySignals(input.matchedSkills(), unresolvedSignals);
        Map<String, TechnologySignals> critical = resolveTechnologySignals(input.missingCriticalSkills(), unresolvedSignals);
        Map<String, TechnologySignals> secondary = resolveTechnologySignals(input.missingSecondarySkills(), unresolvedSignals);
        critical.keySet().forEach(secondary::remove);

        Map<String, ProfessionalSkillEvidence> evidenceBySkill = indexEvidence(input.skillEvidence());
        List<OpportunityKnowledgeEnrichment.Strength> resolvedStrengths = buildStrengths(matched, evidenceBySkill);
        List<OpportunityKnowledgeEnrichment.Transfer> transfers = buildTransfers(
                profileRole,
                opportunityRole,
                input.skillEvidence()
        );
        OpportunityKnowledgeEnrichment.CoverageLevel coverage = coverageLevel(
                profileRole,
                opportunityRole,
                transfers
        );
        OpportunityKnowledgeEnrichment.SecondaryFocus secondaryFocus = secondaryFocus(secondaryRole);
        boolean outOfScope = coverage == OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE;
        List<OpportunityKnowledgeEnrichment.Strength> strengths = outOfScope
                ? List.of()
                : resolvedStrengths;
        List<OpportunityKnowledgeEnrichment.Gap> gaps = outOfScope
                ? List.of()
                : buildGaps(critical, secondary);
        OpportunityKnowledgeEnrichment.SeniorityGuidance seniority = outOfScope
                ? null
                : buildSeniorityGuidance(input.opportunitySeniority(), opportunityRole, input.skillEvidence());
        List<OpportunityKnowledgeEnrichment.Action> actions = outOfScope
                ? List.of()
                : buildActions(critical, secondary, strengths, transfers, opportunityRole);

        List<String> warnings = new ArrayList<>();
        if (outOfScope) {
            warnings.add(catalog.fallbacks().outOfScope());
        } else if (secondaryFocus != null) {
            warnings.add(secondaryFocus.limit());
        }

        return new OpportunityKnowledgeEnrichment(
                OpportunityKnowledgeEnrichment.ContextLevel.SUPPORTED,
                coverage,
                new OpportunityKnowledgeEnrichment.RoleFamily(opportunityRole.id(), opportunityRole.label()),
                secondaryFocus,
                roleExplanation(profileRole, opportunityRole, coverage),
                strengths,
                gaps,
                transfers,
                seniority,
                actions,
                distinct(unresolvedSignals),
                distinct(warnings)
        );
    }

    private OpportunityKnowledgeEnrichment explicitOutOfScopeResult() {
        return new OpportunityKnowledgeEnrichment(
                OpportunityKnowledgeEnrichment.ContextLevel.SUPPORTED,
                OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE,
                null,
                null,
                catalog.fallbacks().outOfScope(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(catalog.fallbacks().outOfScope())
        );
    }

    private OpportunityKnowledgeEnrichment limitedContextResult(
            KnowledgeResolutionInput input,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            KnowledgeCatalog.RoleFamilyDefinition secondaryRole
    ) {
        List<String> warnings = new ArrayList<>();
        if (input.insufficientOpportunityMetadata()) {
            warnings.add(catalog.fallbacks().weakJobMetadata());
        }
        if (opportunityRole == null) {
            warnings.add(catalog.fallbacks().unknownRole());
        }
        String explanation = opportunityRole == null
                ? catalog.fallbacks().weakJobMetadata()
                : opportunityRole.limitedContextCopy();
        return new OpportunityKnowledgeEnrichment(
                OpportunityKnowledgeEnrichment.ContextLevel.LIMITED,
                OpportunityKnowledgeEnrichment.CoverageLevel.LOW_CONTEXT,
                opportunityRole == null
                        ? null
                        : new OpportunityKnowledgeEnrichment.RoleFamily(opportunityRole.id(), opportunityRole.label()),
                secondaryFocus(secondaryRole),
                explanation,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                distinct(warnings)
        );
    }

    private static OpportunityKnowledgeEnrichment.CoverageLevel coverageLevel(
            KnowledgeCatalog.RoleFamilyDefinition profileRole,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            List<OpportunityKnowledgeEnrichment.Transfer> transfers
    ) {
        if (profileRole != null && profileRole.id().equals(opportunityRole.id())) {
            return OpportunityKnowledgeEnrichment.CoverageLevel.DIRECT_COVERAGE;
        }
        if (!transfers.isEmpty()) {
            return OpportunityKnowledgeEnrichment.CoverageLevel.PARTIAL_COVERAGE;
        }
        return OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE;
    }

    private String roleExplanation(
            KnowledgeCatalog.RoleFamilyDefinition profileRole,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole,
            OpportunityKnowledgeEnrichment.CoverageLevel coverage
    ) {
        if (coverage == OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE) {
            return catalog.fallbacks().outOfScope();
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
            if (!hasRequiredSourceEvidence(
                    profileRole,
                    evidence,
                    rule.requiredEvidenceTypes(),
                    rule.requiredSourceTechnologyRefs(),
                    Boolean.TRUE.equals(rule.requiresAllSourceTechnologies())
            )) {
                continue;
            }
            List<String> sourceTechnologies = evidencedTechnologyLabels(
                    evidence,
                    rule.requiredEvidenceTypes(),
                    rule.requiredSourceTechnologyRefs()
            );
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
                    sourceTechnologies,
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
                        && opportunityRole.evidenceDomains().contains(item.domain())
                        && matchesTechnologyRefs(
                        item,
                        opportunityRole.seniorityEvidenceTechnologyRefs()
                ));
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
            List<OpportunityKnowledgeEnrichment.Strength> strengths,
            List<OpportunityKnowledgeEnrichment.Transfer> transfers,
            KnowledgeCatalog.RoleFamilyDefinition opportunityRole
    ) {
        Map<String, OpportunityKnowledgeEnrichment.Action> out = new LinkedHashMap<>();
        addGapActions(out, critical, "Brecha critica detectada");
        for (OpportunityKnowledgeEnrichment.Strength strength : strengths) {
            if (strength.evidenceAssessment() != OpportunityKnowledgeEnrichment.EvidenceAssessment.WEAK) {
                continue;
            }
            KnowledgeCatalog.TechnologyDefinition technology = technologyById.get(strength.technologyId());
            String id = "EVIDENCE_" + technology.id();
            addAction(out, new OpportunityKnowledgeEnrichment.Action(
                    id,
                    technology.evidence().declaredOnlyAction(),
                    technology.id(),
                    "Skill declarada o transferible sin evidencia directa"
            ));
        }
        if (!transfers.isEmpty() && opportunityRole != null && !opportunityRole.concreteActions().isEmpty()) {
            OpportunityKnowledgeEnrichment.Transfer transfer = transfers.get(0);
            addAction(out, new OpportunityKnowledgeEnrichment.Action(
                    "TRANSFER_" + transfer.id(),
                    opportunityRole.concreteActions().get(0),
                    null,
                    "Transferencia parcial explicita"
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
                addAction(out, new OpportunityKnowledgeEnrichment.Action(
                        action.id(),
                        action.text(),
                        technology.id(),
                        reason
                ));
            }
        }
    }

    private static void addAction(
            Map<String, OpportunityKnowledgeEnrichment.Action> out,
            OpportunityKnowledgeEnrichment.Action action
    ) {
        String normalizedText = KnowledgeCatalogValidator.normalize(action.text());
        boolean duplicateText = out.values().stream()
                .anyMatch(existing -> KnowledgeCatalogValidator.normalize(existing.text()).equals(normalizedText));
        if (!duplicateText) {
            out.putIfAbsent(action.id(), action);
        }
    }

    private boolean hasRequiredSourceEvidence(
            KnowledgeCatalog.RoleFamilyDefinition sourceRole,
            List<ProfessionalSkillEvidence> evidence,
            List<ProfessionalEvidenceType> requiredTypes,
            List<String> requiredSourceTechnologyRefs,
            boolean requiresAllSourceTechnologies
    ) {
        boolean sourceDomainEvidence = evidence.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> sourceRole.evidenceDomains().contains(item.domain())
                        && requiredTypes.contains(item.evidenceType()));
        if (!sourceDomainEvidence || requiredSourceTechnologyRefs.isEmpty()) {
            return sourceDomainEvidence;
        }
        Set<String> evidencedTechnologyIds = evidence.stream()
                .filter(Objects::nonNull)
                .filter(item -> requiredTypes.contains(item.evidenceType()))
                .map(this::technologyIdForEvidence)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return requiresAllSourceTechnologies
                ? evidencedTechnologyIds.containsAll(requiredSourceTechnologyRefs)
                : requiredSourceTechnologyRefs.stream().anyMatch(evidencedTechnologyIds::contains);
    }

    private List<String> evidencedTechnologyLabels(
            List<ProfessionalSkillEvidence> evidence,
            List<ProfessionalEvidenceType> requiredTypes,
            List<String> requiredSourceTechnologyRefs
    ) {
        Set<String> allowed = Set.copyOf(requiredSourceTechnologyRefs);
        return evidence.stream()
                .filter(Objects::nonNull)
                .filter(item -> requiredTypes.contains(item.evidenceType()))
                .map(this::technologyIdForEvidence)
                .filter(Objects::nonNull)
                .filter(allowed::contains)
                .distinct()
                .map(technologyById::get)
                .filter(Objects::nonNull)
                .map(KnowledgeCatalog.TechnologyDefinition::label)
                .toList();
    }

    private String technologyIdForEvidence(ProfessionalSkillEvidence evidence) {
        if (evidence == null || evidence.skillName() == null) {
            return null;
        }
        KnowledgeCatalog.TechnologyDefinition technology = technologyByEvidenceSkill.get(
                KnowledgeCatalogValidator.normalize(evidence.skillName())
        );
        return technology == null ? null : technology.id();
    }

    private boolean matchesTechnologyRefs(
            ProfessionalSkillEvidence evidence,
            List<String> technologyRefs
    ) {
        if (technologyRefs.isEmpty()) {
            return true;
        }
        if (evidence == null || evidence.skillName() == null) {
            return false;
        }
        KnowledgeCatalog.TechnologyDefinition technology = technologyByEvidenceSkill.get(
                KnowledgeCatalogValidator.normalize(evidence.skillName())
        );
        return technology != null && technologyRefs.contains(technology.id());
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

    private KnowledgeCatalog.RoleFamilyDefinition resolveSecondaryRole(
            String value,
            KnowledgeCatalog.RoleFamilyDefinition primaryRole
    ) {
        KnowledgeCatalog.RoleFamilyDefinition secondaryRole = resolveRole(value);
        if (secondaryRole == null || primaryRole == null) {
            return secondaryRole;
        }
        return secondaryRole.id().equals(primaryRole.id()) ? null : secondaryRole;
    }

    private static OpportunityKnowledgeEnrichment.SecondaryFocus secondaryFocus(
            KnowledgeCatalog.RoleFamilyDefinition secondaryRole
    ) {
        if (secondaryRole == null) {
            return null;
        }
        return new OpportunityKnowledgeEnrichment.SecondaryFocus(
                secondaryRole.id(),
                secondaryRole.label(),
                "La oferta incluye un foco secundario de " + secondaryRole.label()
                        + "; se informa como limite contextual y no como experiencia demostrada del perfil."
        );
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

    private static Map<String, KnowledgeCatalog.TechnologyDefinition> indexEvidenceSkills(
            List<KnowledgeCatalog.TechnologyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.TechnologyDefinition> out = new LinkedHashMap<>();
        for (KnowledgeCatalog.TechnologyDefinition definition : definitions) {
            LinkedHashSet<String> refs = new LinkedHashSet<>(definition.matchingSkillRefs());
            refs.addAll(definition.evidenceSkillRefs());
            refs.forEach(ref -> out.put(KnowledgeCatalogValidator.normalize(ref), definition));
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
