package com.DataLaburo.web.analysis;

import com.DataLaburo.web.model.Job;
import com.DataLaburo.web.service.SkillExtractionService;
import com.DataLaburo.web.service.SkillExtractionService.ExtractedSkills;
import com.DataLaburo.web.service.SkillExtractionService.SkillCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GapAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisService.class);
    private static final int CRITICAL_LINES_FROM_DESCRIPTION = 8;
    private static final int MIN_STRONG_ALIAS_LENGTH = 3;

    private final SkillExtractionService skillExtractionService;

    public GapAnalysisService(SkillExtractionService skillExtractionService) {
        this.skillExtractionService = skillExtractionService;
    }

    public GapAnalysis analyze(ExtractedSkills candidateSkills, Job job, SkillCatalog catalog) {
        return analyze(null, candidateSkills, job, catalog);
    }

    public GapAnalysis analyze(String candidateText, ExtractedSkills candidateSkills, Job job, SkillCatalog catalog) {
        JobTextBuckets buckets = buildJobTextBuckets(job);
        SkillCatalog analysisCatalog = augmentCatalog(catalog);

        ExtractedSkills critical = skillExtractionService.extractSkills(buckets.criticalText(), analysisCatalog);
        ExtractedSkills secondary = skillExtractionService.extractSkills(buckets.secondaryText(), analysisCatalog);

        Set<Long> candidateIds = strongSkillIds(candidateSkills, candidateText, analysisCatalog, "PROFILE");
        Set<Long> criticalIds = strongSkillIds(critical, buckets.criticalText(), analysisCatalog, "CRITICAL", job);
        Set<Long> secondaryIds = subtract(strongSkillIds(secondary, buckets.secondaryText(), analysisCatalog, "SECONDARY", job), criticalIds);

        Map<Long, List<SkillEvidence>> criticalEvidenceById = evidenceBySkillId(
                critical,
                buckets.criticalText(),
                analysisCatalog,
                "CRITICAL",
                job
        );
        Map<Long, List<SkillEvidence>> secondaryEvidenceById = evidenceBySkillId(
                secondary,
                buckets.secondaryText(),
                analysisCatalog,
                "SECONDARY",
                job
        );

        applySatisfiedAlternativeGroups(criticalIds, secondaryIds, candidateIds, buckets.criticalText(), analysisCatalog, job);

        return analyze(
                namesForIds(analysisCatalog, candidateIds),
                namesForIds(analysisCatalog, criticalIds),
                namesForIds(analysisCatalog, secondaryIds),
                flattenEvidence(criticalEvidenceById, criticalIds),
                flattenEvidence(secondaryEvidenceById, secondaryIds)
        );
    }

    public GapAnalysis analyze(
            Collection<String> candidateSkills,
            Collection<String> criticalSkills,
            Collection<String> secondarySkills
    ) {
        return analyze(candidateSkills, criticalSkills, secondarySkills, List.of(), List.of());
    }

    private GapAnalysis analyze(
            Collection<String> candidateSkills,
            Collection<String> criticalSkills,
            Collection<String> secondarySkills,
            List<SkillEvidence> criticalEvidence,
            List<SkillEvidence> secondaryEvidence
    ) {
        Map<String, String> candidateByNorm = normalizedDisplayMap(candidateSkills);
        Map<String, String> criticalByNorm = normalizedDisplayMap(criticalSkills);
        Map<String, String> secondaryByNorm = normalizedDisplayMap(secondarySkills);

        for (String criticalNorm : criticalByNorm.keySet()) {
            secondaryByNorm.remove(criticalNorm);
        }

        List<String> matched = new ArrayList<>();
        matched.addAll(intersectionDisplay(candidateByNorm, criticalByNorm));
        matched.addAll(intersectionDisplay(candidateByNorm, secondaryByNorm));

        List<String> missingCritical = missingDisplay(candidateByNorm, criticalByNorm);
        List<String> missingSecondary = missingDisplay(candidateByNorm, secondaryByNorm);

        return new GapAnalysis(
                sortedDistinct(matched),
                sortedDistinct(missingCritical),
                sortedDistinct(missingSecondary),
                sortedDistinct(candidateByNorm.values()),
                sortedDistinct(criticalByNorm.values()),
                sortedDistinct(secondaryByNorm.values()),
                criticalEvidence == null ? List.of() : criticalEvidence,
                secondaryEvidence == null ? List.of() : secondaryEvidence
        );
    }

    private static List<String> intersectionDisplay(Map<String, String> candidateByNorm, Map<String, String> targetByNorm) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : targetByNorm.entrySet()) {
            String candidateDisplay = candidateByNorm.get(entry.getKey());
            if (candidateDisplay != null) {
                out.add(candidateDisplay);
            }
        }
        return out;
    }

    private static List<String> missingDisplay(Map<String, String> candidateByNorm, Map<String, String> targetByNorm) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : targetByNorm.entrySet()) {
            if (!candidateByNorm.containsKey(entry.getKey())) {
                out.add(entry.getValue());
            }
        }
        return out;
    }

    private static Map<String, String> normalizedDisplayMap(Collection<String> skills) {
        Map<String, String> out = new LinkedHashMap<>();
        if (skills == null) {
            return out;
        }
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            String display = skill.trim();
            String normalized = SkillExtractionService.normalizeText(display);
            if (!normalized.isBlank()) {
                out.putIfAbsent(normalized, display);
            }
        }
        return out;
    }

    private static List<String> sortedDistinct(Collection<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static Set<String> namesForIds(SkillCatalog catalog, Set<Long> ids) {
        if (catalog == null || catalog.skillIdToName() == null || ids == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Long id : ids) {
            String name = catalog.skillIdToName().get(id);
            if (name != null && !name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    private static Set<Long> strongSkillIds(
            ExtractedSkills extractedSkills,
            String rawText,
            SkillCatalog catalog,
            String source
    ) {
        return strongSkillIds(extractedSkills, rawText, catalog, source, null);
    }

    private static Set<Long> strongSkillIds(
            ExtractedSkills extractedSkills,
            String rawText,
            SkillCatalog catalog,
            String source,
            Job job
    ) {
        Map<Long, List<SkillEvidence>> evidenceById = evidenceBySkillId(extractedSkills, rawText, catalog, source, job);
        Set<Long> out = new LinkedHashSet<>();
        for (Map.Entry<Long, List<SkillEvidence>> entry : evidenceById.entrySet()) {
            boolean strong = entry.getValue().stream()
                    .anyMatch(evidence -> evidence.strength() == SkillEvidenceStrength.STRONG);
            if (strong) {
                out.add(entry.getKey());
            } else {
                logWeakEvidence(entry.getKey(), entry.getValue(), source, job);
            }
        }
        return out;
    }

    private static Map<Long, List<SkillEvidence>> evidenceBySkillId(
            ExtractedSkills extractedSkills,
            String rawText,
            SkillCatalog catalog,
            String source,
            Job job
    ) {
        Map<Long, List<SkillEvidence>> out = new LinkedHashMap<>();
        if (catalog == null || catalog.aliasToSkillId() == null || catalog.skillIdToName() == null) {
            return out;
        }
        String normalizedText = normalizeForEvidence(rawText);
        if (normalizedText.isBlank()) {
            if ("PROFILE".equals(source) && extractedSkills != null && extractedSkills.skillIds() != null) {
                for (Long skillId : extractedSkills.skillIds()) {
                    String skillName = catalog.skillIdToName().get(skillId);
                    if (skillName == null || skillName.isBlank()) {
                        continue;
                    }
                    out.computeIfAbsent(skillId, ignored -> new ArrayList<>()).add(new SkillEvidence(
                            skillName,
                            SkillExtractionService.normalizeText(skillName),
                            source,
                            SkillEvidenceStrength.STRONG,
                            ""
                    ));
                }
            }
            return out;
        }

        for (Map.Entry<String, Long> aliasEntry : catalog.aliasToSkillId().entrySet()) {
            String aliasNormalized = aliasEntry.getKey();
            Long skillId = aliasEntry.getValue();
            if (skillId == null || aliasNormalized == null || aliasNormalized.isBlank()) {
                continue;
            }
            String aliasForEvidence = normalizeForEvidence(aliasNormalized);
            if (!containsNormalizedToken(normalizedText, aliasForEvidence)) {
                continue;
            }

            String skillName = catalog.skillIdToName().get(skillId);
            if (skillName == null || skillName.isBlank()) {
                continue;
            }
            SkillEvidence evidence = new SkillEvidence(
                    skillName,
                    aliasNormalized,
                    source,
                    evidenceStrength(skillName, aliasNormalized, rawText),
                    contextSnippet(rawText, aliasNormalized)
            );
            out.computeIfAbsent(skillId, ignored -> new ArrayList<>()).add(evidence);
            if (log.isDebugEnabled()) {
                Long jobId = job == null ? null : job.getId();
                log.debug(
                        "Skill evidence detected: jobId={} skill={} source={} alias={} strength={} context={}",
                        jobId,
                        evidence.skillName(),
                        evidence.source(),
                        evidence.matchedAlias(),
                        evidence.strength(),
                        evidence.context()
                );
            }
        }
        return out;
    }

    private static Map<Long, List<SkillEvidence>> evidenceBySkillId(
            ExtractedSkills extractedSkills,
            String rawText,
            SkillCatalog catalog,
            String source
    ) {
        return evidenceBySkillId(extractedSkills, rawText, catalog, source, null);
    }

    private static List<SkillEvidence> flattenEvidence(
            Map<Long, List<SkillEvidence>> evidenceById,
            Set<Long> includedIds
    ) {
        if (evidenceById == null || evidenceById.isEmpty() || includedIds == null || includedIds.isEmpty()) {
            return List.of();
        }
        List<SkillEvidence> out = new ArrayList<>();
        for (Long id : includedIds) {
            List<SkillEvidence> evidence = evidenceById.get(id);
            if (evidence != null) {
                out.addAll(evidence);
            }
        }
        return out;
    }

    private static SkillEvidenceStrength evidenceStrength(String skillName, String aliasNormalized, String rawText) {
        String skillNormalized = normalizeForEvidence(skillName);
        String aliasForEvidence = normalizeForEvidence(aliasNormalized);
        if (isDotNetSkill(skillName)) {
            return hasExplicitDotNetEvidence(rawText) ? SkillEvidenceStrength.STRONG : SkillEvidenceStrength.WEAK;
        }
        if (isShortAmbiguousSkill(skillName, aliasForEvidence)) {
            return containsExactCaseToken(rawText, skillName) ? SkillEvidenceStrength.STRONG : SkillEvidenceStrength.WEAK;
        }
        if (aliasForEvidence.equals(skillNormalized)) {
            return SkillEvidenceStrength.STRONG;
        }
        if (aliasForEvidence.replace(" ", "").length() >= MIN_STRONG_ALIAS_LENGTH) {
            return SkillEvidenceStrength.STRONG;
        }
        if (containsExactUppercaseToken(rawText, aliasNormalized)) {
            return SkillEvidenceStrength.STRONG;
        }
        return SkillEvidenceStrength.WEAK;
    }

    private static boolean containsNormalizedToken(String normalizedText, String normalizedNeedle) {
        if (normalizedText == null || normalizedText.isBlank() || normalizedNeedle == null || normalizedNeedle.isBlank()) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + normalizedNeedle + " ");
    }

    private static String normalizeForEvidence(String value) {
        String normalized = SkillExtractionService.normalizeText(value);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}#+]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean isDotNetSkill(String skillName) {
        String normalized = normalizeForEvidence(skillName);
        return "net".equals(normalized);
    }

    private static boolean hasExplicitDotNetEvidence(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile(
                "(?i)(?<![\\p{L}\\p{N}])(?:\\.net|asp\\.net|dotnet|dot\\s+net)(?![\\p{L}\\p{N}])"
        );
        return pattern.matcher(rawText).find();
    }

    private static boolean isShortAmbiguousSkill(String skillName, String aliasForEvidence) {
        String skill = normalizeForEvidence(skillName);
        return ("go".equals(skill) || "c".equals(skill)) && aliasForEvidence.length() <= 2;
    }

    private static boolean containsExactCaseToken(String rawText, String token) {
        if (rawText == null || rawText.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(token.trim()) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(rawText).find();
    }

    private static boolean containsExactUppercaseToken(String rawText, String normalizedAlias) {
        if (rawText == null || rawText.isBlank() || normalizedAlias == null || normalizedAlias.isBlank()) {
            return false;
        }
        if (!normalizedAlias.matches("[a-z]{1,2}")) {
            return false;
        }
        String uppercaseAlias = normalizedAlias.toUpperCase(Locale.ROOT);
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(uppercaseAlias) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(rawText).find();
    }

    private static String contextSnippet(String rawText, String normalizedAlias) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String rawFlat = rawText.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (rawFlat.isBlank()) {
            return "";
        }
        String normalizedFlat = SkillExtractionService.normalizeText(rawFlat);
        int normalizedIndex = normalizedFlat.indexOf(normalizedAlias);
        if (normalizedIndex < 0) {
            return rawFlat.length() <= 140 ? rawFlat : rawFlat.substring(0, 140);
        }
        int approximateStart = Math.max(0, normalizedIndex - 60);
        int approximateEnd = Math.min(rawFlat.length(), normalizedIndex + normalizedAlias.length() + 60);
        if (approximateStart >= approximateEnd) {
            return rawFlat.length() <= 140 ? rawFlat : rawFlat.substring(0, 140);
        }
        return rawFlat.substring(approximateStart, approximateEnd).trim();
    }

    private static void logWeakEvidence(Long skillId, List<SkillEvidence> evidence, String source, Job job) {
        if (!log.isDebugEnabled() || evidence == null || evidence.isEmpty()) {
            return;
        }
        Long jobId = job == null ? null : job.getId();
        String skillName = evidence.get(0).skillName();
        List<String> aliases = evidence.stream()
                .map(SkillEvidence::matchedAlias)
                .distinct()
                .toList();
        log.debug(
                "Ignoring weak-only skill evidence: jobId={} skillId={} skill={} source={} aliases={}",
                jobId,
                skillId,
                skillName,
                source,
                aliases
        );
    }

    private static void applySatisfiedAlternativeGroups(
            Set<Long> criticalIds,
            Set<Long> secondaryIds,
            Set<Long> candidateIds,
            String criticalText,
            SkillCatalog catalog,
            Job job
    ) {
        if (criticalIds == null || criticalIds.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return;
        }
        for (Set<Long> group : alternativeGroups(criticalText, catalog)) {
            boolean hasCandidateAlternative = group.stream().anyMatch(candidateIds::contains);
            if (!hasCandidateAlternative) {
                continue;
            }
            Set<Long> removed = new LinkedHashSet<>();
            for (Long id : group) {
                if (candidateIds.contains(id)) {
                    continue;
                }
                if (criticalIds.remove(id)) {
                    removed.add(id);
                }
                if (secondaryIds != null) {
                    secondaryIds.remove(id);
                }
            }
            if (log.isDebugEnabled() && !removed.isEmpty()) {
                log.debug(
                        "Suppressing satisfied alternative gaps: jobId={} satisfied={} suppressed={}",
                        job == null ? null : job.getId(),
                        namesForIds(catalog, intersect(group, candidateIds)),
                        namesForIds(catalog, removed)
                );
            }
        }
    }

    private static List<Set<Long>> alternativeGroups(String rawText, SkillCatalog catalog) {
        if (rawText == null || rawText.isBlank() || catalog == null) {
            return List.of();
        }
        List<Set<Long>> groups = new ArrayList<>();
        String[] lines = rawText.replace("\r", "").split("\n");
        for (String line : lines) {
            if (!hasAlternativeConnector(line)) {
                continue;
            }
            Set<Long> ids = isSlashOnlyAlternative(line)
                    ? slashAlternativeIds(line, catalog)
                    : strongSkillIds(null, line, catalog, "ALTERNATIVE");
            if (ids.size() >= 2) {
                groups.add(ids);
            }
        }
        return groups;
    }

    private static boolean hasAlternativeConnector(String line) {
        String normalized = normalizeForEvidence(line);
        if (normalized.isBlank()) {
            return false;
        }
        if ((" " + normalized + " ").contains(" o ") || (" " + normalized + " ").contains(" or ")) {
            return true;
        }
        return line != null && line.contains("/");
    }

    private static boolean isSlashOnlyAlternative(String line) {
        if (line == null || !line.contains("/")) {
            return false;
        }
        String normalized = " " + normalizeForEvidence(line) + " ";
        return !normalized.contains(" o ") && !normalized.contains(" or ");
    }

    private static Set<Long> slashAlternativeIds(String line, SkillCatalog catalog) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : line.split("/")) {
            Set<Long> partIds = mostSpecificSkillIds(strongSkillIds(null, part, catalog, "ALTERNATIVE"), catalog);
            if (partIds.size() == 1) {
                ids.add(partIds.iterator().next());
            }
        }
        return isKnownAlternativeFamily(ids, catalog) ? ids : Set.of();
    }

    private static Set<Long> mostSpecificSkillIds(Set<Long> ids, SkillCatalog catalog) {
        if (ids == null || ids.size() <= 1 || catalog == null || catalog.skillIdToName() == null) {
            return ids == null ? Set.of() : ids;
        }
        Set<Long> out = new LinkedHashSet<>(ids);
        for (Long id : ids) {
            String name = catalog.skillIdToName().get(id);
            String normalized = normalizeForEvidence(name);
            if (normalized.isBlank()) {
                continue;
            }
            boolean coveredByMoreSpecificSkill = ids.stream()
                    .filter(otherId -> !otherId.equals(id))
                    .map(catalog.skillIdToName()::get)
                    .map(GapAnalysisService::normalizeForEvidence)
                    .anyMatch(other -> other.length() > normalized.length()
                            && containsNormalizedToken(other, normalized));
            if (coveredByMoreSpecificSkill) {
                out.remove(id);
            }
        }
        return out;
    }

    private static boolean isKnownAlternativeFamily(Set<Long> ids, SkillCatalog catalog) {
        if (ids == null || ids.size() < 2 || catalog == null || catalog.skillIdToName() == null) {
            return false;
        }
        Set<String> names = ids.stream()
                .map(catalog.skillIdToName()::get)
                .filter(name -> name != null && !name.isBlank())
                .map(GapAnalysisService::normalizeForEvidence)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> languages = Set.of("java", "go", "kotlin", "c#", "c++", "python", "ruby", "javascript", "typescript");
        Set<String> databases = Set.of("postgresql", "oracle", "sql server", "mysql");
        return languages.containsAll(names) || databases.containsAll(names);
    }

    private static Set<Long> intersect(Set<Long> left, Set<Long> right) {
        Set<Long> out = new LinkedHashSet<>();
        if (left == null || right == null) {
            return out;
        }
        for (Long id : left) {
            if (right.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static Set<Long> subtract(Set<Long> from, Set<Long> remove) {
        Set<Long> out = new LinkedHashSet<>();
        if (from == null) {
            return out;
        }
        for (Long id : from) {
            if (remove == null || !remove.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static JobTextBuckets buildJobTextBuckets(Job job) {
        if (job == null) {
            return new JobTextBuckets("", "");
        }
        String critical = joinNonBlank(
                job.getTitle(),
                job.getRequirementsText(),
                firstLines(job.getDescription(), CRITICAL_LINES_FROM_DESCRIPTION),
                hasMeaningfulJobBody(job) ? "" : firstLines(job.getVisibleText(), CRITICAL_LINES_FROM_DESCRIPTION)
        );
        String secondary = joinNonBlank(
                removeFirstLines(job.getDescription(), CRITICAL_LINES_FROM_DESCRIPTION),
                hasMeaningfulJobBody(job) ? "" : removeFirstLines(job.getVisibleText(), CRITICAL_LINES_FROM_DESCRIPTION)
        );
        return new JobTextBuckets(critical, secondary);
    }

    private static boolean hasMeaningfulJobBody(Job job) {
        if (job == null) {
            return false;
        }
        return hasText(job.getRequirementsText()) || hasText(job.getDescription());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String firstLines(String value, int maxLines) {
        if (value == null || value.isBlank() || maxLines <= 0) {
            return "";
        }
        String[] lines = value.replace("\r", "").split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            if (lines[i] == null || lines[i].isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String removeFirstLines(String value, int skipLines) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (skipLines <= 0) {
            return value;
        }
        String[] lines = value.replace("\r", "").split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = skipLines; i < lines.length; i++) {
            if (lines[i] == null || lines[i].isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private record JobTextBuckets(String criticalText, String secondaryText) {
    }

    private static SkillCatalog augmentCatalog(SkillCatalog catalog) {
        Map<String, Long> aliasToSkillId = new LinkedHashMap<>();
        Map<Long, String> skillIdToName = new LinkedHashMap<>();
        if (catalog != null) {
            if (catalog.aliasToSkillId() != null) {
                aliasToSkillId.putAll(catalog.aliasToSkillId());
            }
            if (catalog.skillIdToName() != null) {
                skillIdToName.putAll(catalog.skillIdToName());
            }
        }

        long[] nextSyntheticId = {-1L};
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "PostgreSQL", "Postgre SQL", "PostgreSQL", "Postgres");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "Oracle", "Oracle DB", "Oracle Database");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "SQL Server", "Microsoft SQL Server", "MSSQL", "MS SQL");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "PL/SQL", "PL SQL", "PLSQL");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "ITIL", "ITIL processes");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "ITSM", "ITSM Operations");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "Windows Server", "Microsoft Windows Server");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "JBoss", "JBoss EAP", "Red Hat JBoss");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "IAM", "Identity and Access Management", "Identity & Access Management");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "OAuth");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "OIDC");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "SAML");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "PowerShell");
        addSkill(aliasToSkillId, skillIdToName, nextSyntheticId, "Ruby");

        return new SkillCatalog(aliasToSkillId, skillIdToName);
    }

    private static void addSkill(
            Map<String, Long> aliasToSkillId,
            Map<Long, String> skillIdToName,
            long[] nextSyntheticId,
            String skillName,
            String... aliases
    ) {
        Long id = findSkillId(skillIdToName, skillName);
        if (id == null) {
            id = nextSyntheticId[0]--;
            skillIdToName.put(id, skillName);
        }
        addAlias(aliasToSkillId, id, skillName);
        if (aliases != null) {
            for (String alias : aliases) {
                addAlias(aliasToSkillId, id, alias);
            }
        }
    }

    private static Long findSkillId(Map<Long, String> skillIdToName, String skillName) {
        String expected = normalizeForEvidence(skillName);
        for (Map.Entry<Long, String> entry : skillIdToName.entrySet()) {
            if (expected.equals(normalizeForEvidence(entry.getValue()))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void addAlias(Map<String, Long> aliasToSkillId, Long id, String alias) {
        String normalized = SkillExtractionService.normalizeText(alias);
        if (id != null && !normalized.isBlank()) {
            aliasToSkillId.putIfAbsent(normalized, id);
        }
    }
}
