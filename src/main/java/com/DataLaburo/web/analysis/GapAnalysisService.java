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

        ExtractedSkills critical = skillExtractionService.extractSkills(buckets.criticalText(), catalog);
        ExtractedSkills secondary = skillExtractionService.extractSkills(buckets.secondaryText(), catalog);

        Set<Long> candidateIds = strongSkillIds(candidateSkills, candidateText, catalog, "PROFILE");
        Set<Long> criticalIds = strongSkillIds(critical, buckets.criticalText(), catalog, "CRITICAL", job);
        Set<Long> secondaryIds = subtract(strongSkillIds(secondary, buckets.secondaryText(), catalog, "SECONDARY", job), criticalIds);

        Map<Long, List<SkillEvidence>> criticalEvidenceById = evidenceBySkillId(
                critical,
                buckets.criticalText(),
                catalog,
                "CRITICAL",
                job
        );
        Map<Long, List<SkillEvidence>> secondaryEvidenceById = evidenceBySkillId(
                secondary,
                buckets.secondaryText(),
                catalog,
                "SECONDARY",
                job
        );

        return analyze(
                namesForIds(catalog, candidateIds),
                namesForIds(catalog, criticalIds),
                namesForIds(catalog, secondaryIds),
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
                firstLines(job.getVisibleText(), CRITICAL_LINES_FROM_DESCRIPTION)
        );
        String secondary = joinNonBlank(
                removeFirstLines(job.getDescription(), CRITICAL_LINES_FROM_DESCRIPTION),
                removeFirstLines(job.getVisibleText(), CRITICAL_LINES_FROM_DESCRIPTION)
        );
        return new JobTextBuckets(critical, secondary);
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
}
