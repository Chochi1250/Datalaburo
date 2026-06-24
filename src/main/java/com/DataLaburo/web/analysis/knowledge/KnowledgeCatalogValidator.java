package com.DataLaburo.web.analysis.knowledge;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class KnowledgeCatalogValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Set<String> SUPPORTED_SENIORITY_IDS = Set.of(
            "TRAINEE", "JUNIOR", "MID", "SENIOR", "LEAD"
    );

    public void validate(KnowledgeCatalog catalog) {
        if (catalog == null) {
            fail("catalog is null");
        }
        if (catalog.version() != 1) {
            fail("unsupported version " + catalog.version() + "; expected 1");
        }
        if (catalog.roleFamilies().isEmpty()) {
            fail("roleFamilies must not be empty");
        }
        if (catalog.technologies().isEmpty()) {
            fail("technologies must not be empty");
        }

        Map<String, KnowledgeCatalog.RoleFamilyDefinition> roles = indexRoles(catalog.roleFamilies());
        Map<String, KnowledgeCatalog.TechnologyDefinition> technologies = indexTechnologies(catalog.technologies());
        validateRoleAliases(roles.values());
        validateExplicitOutOfScopeAliases(catalog.explicitOutOfScopeRoleAliases(), roles.values());
        validateRoleReferences(roles.values(), technologies.keySet());
        validateTechnologyDefinitions(technologies);
        validateTransfers(catalog.transfers(), roles.keySet(), technologies.keySet());
        validateSeniorityRules(catalog.seniorityRules());
        validateFallbacks(catalog.fallbacks());
    }

    private static Map<String, KnowledgeCatalog.RoleFamilyDefinition> indexRoles(
            List<KnowledgeCatalog.RoleFamilyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.RoleFamilyDefinition> out = new LinkedHashMap<>();
        for (KnowledgeCatalog.RoleFamilyDefinition definition : definitions) {
            if (definition == null) {
                fail("roleFamilies contains a null entry");
            }
            String id = requiredId(definition.id(), "role family");
            if (out.putIfAbsent(id, definition) != null) {
                fail("duplicate role family id " + id);
            }
            requireText(definition.label(), "role family " + id + " label");
            requireText(definition.alignedCopy(), "role family " + id + " alignedCopy");
            requireText(definition.transitionCopy(), "role family " + id + " transitionCopy");
            requireText(definition.limitedContextCopy(), "role family " + id + " limitedContextCopy");
            if (definition.evidenceDomains().isEmpty()) {
                fail("role family " + id + " must declare evidenceDomains");
            }
            if (definition.coreTechnologyRefs().isEmpty()) {
                fail("role family " + id + " must declare coreTechnologyRefs");
            }
            validateTextList(definition.favorableSignals(), "role family " + id + " favorableSignals");
            validateTextList(definition.strongEvidenceSignals(), "role family " + id + " strongEvidenceSignals");
            validateTextList(definition.supportingEvidenceSignals(), "role family " + id + " supportingEvidenceSignals");
            validateTextList(definition.insufficientEvidenceSignals(), "role family " + id + " insufficientEvidenceSignals");
            validateTextList(definition.frequentGapExplanations(), "role family " + id + " frequentGapExplanations");
            validateTextList(definition.concreteActions(), "role family " + id + " concreteActions");
            validateTextList(definition.projectEvidenceIdeas(), "role family " + id + " projectEvidenceIdeas");
            validateTextList(definition.cvIdeas(), "role family " + id + " cvIdeas");
            validateTextList(definition.shortRoadmap(), "role family " + id + " shortRoadmap");
        }
        return out;
    }

    private static Map<String, KnowledgeCatalog.TechnologyDefinition> indexTechnologies(
            List<KnowledgeCatalog.TechnologyDefinition> definitions
    ) {
        Map<String, KnowledgeCatalog.TechnologyDefinition> out = new LinkedHashMap<>();
        for (KnowledgeCatalog.TechnologyDefinition definition : definitions) {
            if (definition == null) {
                fail("technologies contains a null entry");
            }
            String id = requiredId(definition.id(), "technology");
            if (out.putIfAbsent(id, definition) != null) {
                fail("duplicate technology id " + id);
            }
            requireText(definition.label(), "technology " + id + " label");
            if (definition.matchingSkillRefs().isEmpty()) {
                fail("technology " + id + " must declare matchingSkillRefs");
            }
            if (definition.evidenceSkillRefs().isEmpty()) {
                fail("technology " + id + " must declare evidenceSkillRefs");
            }
            validateExactReferences(definition.matchingSkillRefs(), "technology " + id + " matchingSkillRefs");
            validateExactReferences(definition.evidenceSkillRefs(), "technology " + id + " evidenceSkillRefs");
        }
        return out;
    }

    private static void validateRoleAliases(Iterable<KnowledgeCatalog.RoleFamilyDefinition> roles) {
        Map<String, String> ownerByAlias = new LinkedHashMap<>();
        for (KnowledgeCatalog.RoleFamilyDefinition role : roles) {
            Set<String> localAliases = new LinkedHashSet<>();
            localAliases.add(role.id());
            localAliases.add(role.label());
            localAliases.addAll(role.aliases());
            for (String alias : localAliases) {
                requireText(alias, "role family " + role.id() + " alias");
                String normalized = normalize(alias);
                String currentOwner = ownerByAlias.putIfAbsent(normalized, role.id());
                if (currentOwner != null && !currentOwner.equals(role.id())) {
                    fail("role alias '" + alias + "' is shared by " + currentOwner + " and " + role.id());
                }
            }
        }
    }

    private static void validateRoleReferences(
            Iterable<KnowledgeCatalog.RoleFamilyDefinition> roles,
            Set<String> technologyIds
    ) {
        for (KnowledgeCatalog.RoleFamilyDefinition role : roles) {
            for (String ref : role.coreTechnologyRefs()) {
                requireExistingRef(ref, technologyIds, "role family " + role.id() + " coreTechnologyRefs");
            }
            for (String ref : role.seniorityEvidenceTechnologyRefs()) {
                requireExistingRef(ref, technologyIds, "role family " + role.id() + " seniorityEvidenceTechnologyRefs");
            }
        }
    }

    private static void validateExplicitOutOfScopeAliases(
            List<String> aliases,
            Iterable<KnowledgeCatalog.RoleFamilyDefinition> roles
    ) {
        if (aliases.isEmpty()) {
            fail("explicitOutOfScopeRoleAliases must not be empty");
        }
        Set<String> roleAliases = new LinkedHashSet<>();
        for (KnowledgeCatalog.RoleFamilyDefinition role : roles) {
            roleAliases.add(normalize(role.id()));
            roleAliases.add(normalize(role.label()));
            role.aliases().stream().map(KnowledgeCatalogValidator::normalize).forEach(roleAliases::add);
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String alias : aliases) {
            requireText(alias, "explicitOutOfScopeRoleAliases");
            String key = normalize(alias);
            if (!normalized.add(key)) {
                fail("explicitOutOfScopeRoleAliases contains duplicate alias '" + alias + "'");
            }
            if (roleAliases.contains(key)) {
                fail("explicit out-of-scope alias '" + alias + "' overlaps a supported role family");
            }
        }
    }

    private static void validateTechnologyDefinitions(
            Map<String, KnowledgeCatalog.TechnologyDefinition> technologies
    ) {
        Set<String> matchingRefs = new LinkedHashSet<>();
        Set<String> evidenceRefs = new LinkedHashSet<>();
        Set<String> actionIds = new LinkedHashSet<>();
        for (KnowledgeCatalog.TechnologyDefinition technology : technologies.values()) {
            for (String ref : technology.matchingSkillRefs()) {
                String normalized = normalize(ref);
                if (!matchingRefs.add(normalized)) {
                    fail("matching skill reference '" + ref + "' is assigned to more than one technology");
                }
            }
            for (String ref : technology.evidenceSkillRefs()) {
                String normalized = normalize(ref);
                if (!evidenceRefs.add(normalized)) {
                    fail("evidence skill reference '" + ref + "' is assigned to more than one technology");
                }
            }
            for (String ref : technology.relatedTechnologyRefs()) {
                requireExistingRef(ref, technologies.keySet(), "technology " + technology.id() + " relatedTechnologyRefs");
                if (technology.id().equals(ref)) {
                    fail("technology " + technology.id() + " cannot reference itself as related");
                }
            }
            validateEvidenceRule(technology.id(), technology.evidence());
            validateGapRule(technology.id(), technology.gaps());
            if (technology.actions().isEmpty()) {
                fail("technology " + technology.id() + " must declare at least one action");
            }
            for (KnowledgeCatalog.ActionDefinition action : technology.actions()) {
                if (action == null) {
                    fail("technology " + technology.id() + " contains a null action");
                }
                String actionId = requiredId(action.id(), "action");
                if (!actionIds.add(actionId)) {
                    fail("duplicate action id " + actionId);
                }
                requireText(action.text(), "action " + actionId + " text");
            }
            validateTextList(technology.projectIdeas(), "technology " + technology.id() + " projectIdeas");
            validateTextList(technology.cvIdeas(), "technology " + technology.id() + " cvIdeas");
            validateTextList(technology.shortRoadmap(), "technology " + technology.id() + " shortRoadmap");
        }
    }

    private static void validateEvidenceRule(String technologyId, KnowledgeCatalog.EvidenceRule evidence) {
        if (evidence == null) {
            fail("technology " + technologyId + " must declare evidence rules");
        }
        if (evidence.strongTypes().isEmpty() || evidence.supportingTypes().isEmpty() || evidence.weakTypes().isEmpty()) {
            fail("technology " + technologyId + " evidence type groups must not be empty");
        }
        Set<Object> duplicateCheck = new HashSet<>();
        evidence.strongTypes().forEach(type -> addUniqueEvidenceType(duplicateCheck, type, technologyId));
        evidence.supportingTypes().forEach(type -> addUniqueEvidenceType(duplicateCheck, type, technologyId));
        evidence.weakTypes().forEach(type -> addUniqueEvidenceType(duplicateCheck, type, technologyId));
        requireText(evidence.strongCopy(), "technology " + technologyId + " evidence strongCopy");
        requireText(evidence.supportingCopy(), "technology " + technologyId + " evidence supportingCopy");
        requireText(evidence.weakCopy(), "technology " + technologyId + " evidence weakCopy");
        requireText(evidence.declaredOnlyAction(), "technology " + technologyId + " evidence declaredOnlyAction");
    }

    private static void addUniqueEvidenceType(Set<Object> seen, Object type, String technologyId) {
        if (type == null || !seen.add(type)) {
            fail("technology " + technologyId + " has a null or repeated evidence type");
        }
    }

    private static void validateGapRule(String technologyId, KnowledgeCatalog.GapRule gaps) {
        if (gaps == null) {
            fail("technology " + technologyId + " must declare gap rules");
        }
        requireText(gaps.criticalExplanation(), "technology " + technologyId + " critical gap explanation");
        requireText(gaps.secondaryExplanation(), "technology " + technologyId + " secondary gap explanation");
        if (gaps.evidenceIdeas().isEmpty()) {
            fail("technology " + technologyId + " must declare gap evidenceIdeas");
        }
        validateTextList(gaps.evidenceIdeas(), "technology " + technologyId + " gap evidenceIdeas");
    }

    private static void validateTransfers(
            List<KnowledgeCatalog.TransferRule> transfers,
            Set<String> roleIds,
            Set<String> technologyIds
    ) {
        Set<String> ids = new LinkedHashSet<>();
        for (KnowledgeCatalog.TransferRule transfer : transfers) {
            if (transfer == null) {
                fail("transfers contains a null entry");
            }
            String id = requiredId(transfer.id(), "transfer");
            if (!ids.add(id)) {
                fail("duplicate transfer id " + id);
            }
            requireExistingRef(transfer.fromRoleRef(), roleIds, "transfer " + id + " fromRoleRef");
            requireExistingRef(transfer.toRoleRef(), roleIds, "transfer " + id + " toRoleRef");
            if (transfer.fromRoleRef().equals(transfer.toRoleRef())) {
                fail("transfer " + id + " must connect different role families");
            }
            if (transfer.strength() == null) {
                fail("transfer " + id + " must declare strength");
            }
            if (transfer.targetTechnologyRefs().isEmpty()) {
                fail("transfer " + id + " must declare targetTechnologyRefs");
            }
            for (String ref : transfer.requiredSourceTechnologyRefs()) {
                requireExistingRef(ref, technologyIds, "transfer " + id + " requiredSourceTechnologyRefs");
            }
            for (String ref : transfer.targetTechnologyRefs()) {
                requireExistingRef(ref, technologyIds, "transfer " + id + " targetTechnologyRefs");
            }
            if (transfer.requiredEvidenceTypes().isEmpty()) {
                fail("transfer " + id + " must declare requiredEvidenceTypes");
            }
            validateTextList(transfer.transferableConcepts(), "transfer " + id + " transferableConcepts");
            requireText(transfer.warning(), "transfer " + id + " warning");
        }
    }

    private static void validateSeniorityRules(List<KnowledgeCatalog.SeniorityRule> rules) {
        Set<String> ids = new LinkedHashSet<>();
        for (KnowledgeCatalog.SeniorityRule rule : rules) {
            if (rule == null) {
                fail("seniorityRules contains a null entry");
            }
            String id = requiredId(rule.id(), "seniority rule");
            if (!SUPPORTED_SENIORITY_IDS.contains(id)) {
                fail("unsupported seniority rule id " + id);
            }
            if (!ids.add(id)) {
                fail("duplicate seniority rule id " + id);
            }
            if (rule.favorableEvidenceTypes().isEmpty()) {
                fail("seniority rule " + id + " must declare favorableEvidenceTypes");
            }
            requireText(rule.copy(), "seniority rule " + id + " copy");
        }
        if (!ids.equals(SUPPORTED_SENIORITY_IDS)) {
            Set<String> missing = new LinkedHashSet<>(SUPPORTED_SENIORITY_IDS);
            missing.removeAll(ids);
            fail("missing seniority rules " + missing);
        }
    }

    private static void validateFallbacks(KnowledgeCatalog.FallbackCopy fallbacks) {
        if (fallbacks == null) {
            fail("fallbacks must be declared");
        }
        requireText(fallbacks.unknownRole(), "fallbacks.unknownRole");
        requireText(fallbacks.declaredOnly(), "fallbacks.declaredOnly");
        requireText(fallbacks.weakJobMetadata(), "fallbacks.weakJobMetadata");
        requireText(fallbacks.outOfScope(), "fallbacks.outOfScope");
    }

    private static void validateExactReferences(List<String> values, String field) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            requireText(value, field);
            String key = normalize(value);
            if (!normalized.add(key)) {
                fail(field + " contains duplicate exact reference '" + value + "'");
            }
        }
    }

    private static void validateTextList(List<String> values, String field) {
        if (values.isEmpty()) {
            fail(field + " must not be empty");
        }
        values.forEach(value -> requireText(value, field));
    }

    private static void requireExistingRef(String ref, Set<String> ids, String field) {
        requireText(ref, field);
        if (!ids.contains(ref)) {
            fail(field + " references missing id " + ref);
        }
    }

    private static String requiredId(String value, String kind) {
        requireText(value, kind + " id");
        if (!ID_PATTERN.matcher(value).matches()) {
            fail(kind + " id '" + value + "' must match " + ID_PATTERN.pattern());
        }
        return value;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            fail(field + " must not be blank");
        }
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9#+.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private static void fail(String message) {
        throw new KnowledgeCatalogException("Invalid Knowledge Catalog: " + message);
    }
}
