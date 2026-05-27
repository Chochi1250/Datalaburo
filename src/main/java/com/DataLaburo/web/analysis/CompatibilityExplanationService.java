package com.DataLaburo.web.analysis;

import com.DataLaburo.web.service.RuleBasedEnrichmentService;
import com.DataLaburo.web.service.SkillExtractionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CompatibilityExplanationService {
    private static final Set<String> GENERIC_EVIDENCE_SKILLS = Set.of("git", "rest", "sql");

    public CompatibilityExplanation explain(
            String profileText,
            double vectorSimilarity,
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            RuleBasedEnrichmentService.EnrichedDocument profileEnriched,
            RuleBasedEnrichmentService.EnrichedDocument jobEnriched
    ) {
        return explain(
                profileText,
                vectorSimilarity,
                gapAnalysis,
                transferableSkills,
                profileEnriched,
                jobEnriched,
                CompatibilitySignalContext.empty()
        );
    }

    public CompatibilityExplanation explain(
            String profileText,
            double vectorSimilarity,
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            RuleBasedEnrichmentService.EnrichedDocument profileEnriched,
            RuleBasedEnrichmentService.EnrichedDocument jobEnriched,
            CompatibilitySignalContext context
    ) {
        EvidenceLevel evidenceLevel = detectEvidenceLevel(profileText, gapAnalysis, transferableSkills);
        boolean roleAligned = roleAligned(profileEnriched, jobEnriched) || roleAligned(context);
        CompatibilityCategory category = assignCategory(
                gapAnalysis,
                transferableSkills,
                evidenceLevel,
                vectorSimilarity,
                roleAligned,
                context
        );
        return new CompatibilityExplanation(
                category,
                evidenceLevel,
                roadmapSuggestions(gapAnalysis, transferableSkills, category),
                explanationFor(category, gapAnalysis, transferableSkills, roleAligned),
                confidenceFor(category, evidenceLevel, gapAnalysis, transferableSkills)
        );
    }

    public CompatibilityCategory assignCategory(
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            EvidenceLevel evidenceLevel,
            double vectorSimilarity,
            boolean roleAligned
    ) {
        return assignCategory(
                gapAnalysis,
                transferableSkills,
                evidenceLevel,
                vectorSimilarity,
                roleAligned,
                CompatibilitySignalContext.empty()
        );
    }

    public CompatibilityCategory assignCategory(
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            EvidenceLevel evidenceLevel,
            double vectorSimilarity,
            boolean roleAligned,
            CompatibilitySignalContext context
    ) {
        int matched = gapAnalysis == null ? 0 : gapAnalysis.directMatchCount();
        int criticalGaps = gapAnalysis == null ? 0 : gapAnalysis.criticalGapCount();
        int secondaryGaps = gapAnalysis == null ? 0 : gapAnalysis.secondaryGapCount();
        boolean hasTransfer = transferableSkills != null && !transferableSkills.isEmpty();
        boolean genericOnly = matched > 0 && hasOnlyGenericMatchedSkills(gapAnalysis);
        int seniorityDelta = seniorityDelta(context);
        boolean strongEvidence = evidenceLevel == EvidenceLevel.WORK_EXPERIENCE
                || evidenceLevel == EvidenceLevel.PROJECT
                || evidenceLevel == EvidenceLevel.CERTIFICATION;

        if (matched == 0 && !hasTransfer && !roleAligned) {
            return criticalGaps > 0 || secondaryGaps > 0
                    ? CompatibilityCategory.LEARNING_ROADMAP_ONLY
                    : CompatibilityCategory.LOW_FIT;
        }
        if (genericOnly && !roleAligned) {
            if (seniorityDelta >= 2 || criticalGaps > 0) {
                return CompatibilityCategory.LOW_FIT;
            }
            return CompatibilityCategory.ASPIRATIONAL_MATCH;
        }

        CompatibilityCategory category;
        if (matched >= 3 && criticalGaps == 0 && strongEvidence) {
            category = CompatibilityCategory.STRONG_MATCH;
        } else if (matched > 0 && criticalGaps == 0) {
            category = CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS;
        } else if (hasTransfer && (roleAligned || matched > 0 || vectorSimilarity >= 0.50d)) {
            category = CompatibilityCategory.TRANSFERABLE_OPPORTUNITY;
        } else if (matched > 0
                && evidenceLevel == EvidenceLevel.MENTIONED_ONLY
                && criticalGaps > 0
                && !hasTransfer) {
            category = CompatibilityCategory.KEYWORD_MATCH_RISK;
        } else if (matched > 0 || roleAligned || vectorSimilarity >= 0.55d) {
            category = CompatibilityCategory.ASPIRATIONAL_MATCH;
        } else if (criticalGaps > 0 || secondaryGaps > 0) {
            category = CompatibilityCategory.LEARNING_ROADMAP_ONLY;
        } else {
            category = CompatibilityCategory.LOW_FIT;
        }

        return applySeniorityDowngrade(category, seniorityDelta, matched, criticalGaps, genericOnly);
    }

    public EvidenceLevel detectEvidenceLevel(
            String profileText,
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills
    ) {
        boolean hasDirectEvidence = gapAnalysis != null && gapAnalysis.directMatchCount() > 0;
        boolean hasTransfer = transferableSkills != null && !transferableSkills.isEmpty();
        if (!hasDirectEvidence) {
            return hasTransfer ? EvidenceLevel.TRANSFERABLE : EvidenceLevel.NO_EVIDENCE;
        }

        String normalized = SkillExtractionService.normalizeText(profileText);
        if (normalized.isBlank()) {
            return EvidenceLevel.MENTIONED_ONLY;
        }
        if (containsAny(normalized,
                "experience", "experiencia", "worked", "trabaje", "trabajo", "desarrolle",
                "implemente", "built", "mantuve", "produccion", "production")) {
            return EvidenceLevel.WORK_EXPERIENCE;
        }
        if (containsAny(normalized, "project", "projects", "proyecto", "proyectos", "portfolio", "github")) {
            return EvidenceLevel.PROJECT;
        }
        if (containsAny(normalized, "certification", "certificado", "certificacion", "certificate")) {
            return EvidenceLevel.CERTIFICATION;
        }
        if (containsAny(normalized, "university", "universidad", "academic", "academico", "bootcamp", "curso", "carrera")) {
            return EvidenceLevel.ACADEMIC;
        }
        return EvidenceLevel.MENTIONED_ONLY;
    }

    private static boolean roleAligned(
            RuleBasedEnrichmentService.EnrichedDocument profileEnriched,
            RuleBasedEnrichmentService.EnrichedDocument jobEnriched
    ) {
        if (profileEnriched == null || jobEnriched == null) {
            return false;
        }
        if (profileEnriched.categories() == null || jobEnriched.categories() == null) {
            return false;
        }
        for (RuleBasedEnrichmentService.Category category : profileEnriched.categories()) {
            if (jobEnriched.categories().contains(category)) {
                return true;
            }
        }
        return false;
    }

    private static boolean roleAligned(CompatibilitySignalContext context) {
        String role = normalize(context == null ? null : context.detectedRole());
        if (role.isBlank()) {
            return false;
        }
        return switch (role) {
            case "backend", "full_stack", "dotnet_backend", "dotnet_fullstack", "database" -> true;
            default -> false;
        };
    }

    private static List<String> roadmapSuggestions(
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            CompatibilityCategory category
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        if (gapAnalysis != null) {
            for (String skill : gapAnalysis.missingCriticalSkills()) {
                putSuggestion(out, "gap:critical:" + skill, "Cubrir base practica de " + skill);
            }
            for (String skill : gapAnalysis.missingSecondarySkills()) {
                putSuggestion(out, "gap:secondary:" + skill, "Profundizar " + skill + " basico");
            }
            if (out.isEmpty() && !gapAnalysis.matchedSkills().isEmpty()) {
                String skill = gapAnalysis.matchedSkills().get(0);
                putSuggestion(out, "evidence:" + skill, "Preparar evidencia concreta sobre " + skill);
            }
        }
        if (transferableSkills != null) {
            for (TransferableSkill transferable : transferableSkills) {
                String from = canonicalRoadmapSignal(transferable.from());
                String to = canonicalRoadmapSignal(transferable.to());
                putSuggestion(
                        out,
                        "transfer:" + from + "->" + to,
                        "Convertir " + from + " en practica concreta de " + to
                );
            }
        }
        if (out.isEmpty() && category == CompatibilityCategory.LOW_FIT) {
            putSuggestion(out, "low-fit", "Usar esta oferta solo como referencia de aprendizaje");
        }
        return out.values().stream().limit(5).toList();
    }

    private static void putSuggestion(Map<String, String> out, String key, String suggestion) {
        if (out == null || key == null || suggestion == null || suggestion.isBlank()) {
            return;
        }
        String normalizedKey = SkillExtractionService.normalizeText(key);
        if (!normalizedKey.isBlank()) {
            out.putIfAbsent(normalizedKey, suggestion);
        }
    }

    private static String canonicalRoadmapSignal(String value) {
        String normalized = SkillExtractionService.normalizeText(value);
        return switch (normalized) {
            case "backend development", "backend developer", "backend web" -> "Backend";
            default -> value == null ? "" : value.trim();
        };
    }

    private static String explanationFor(
            CompatibilityCategory category,
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills,
            boolean roleAligned
    ) {
        int matched = gapAnalysis == null ? 0 : gapAnalysis.directMatchCount();
        int criticalGaps = gapAnalysis == null ? 0 : gapAnalysis.criticalGapCount();
        int secondaryGaps = gapAnalysis == null ? 0 : gapAnalysis.secondaryGapCount();
        boolean hasTransfer = transferableSkills != null && !transferableSkills.isEmpty();

        return switch (category) {
            case STRONG_MATCH -> "La oferta esta cerca semanticamente y comparte un nucleo tecnico fuerte, sin brechas criticas detectadas.";
            case GOOD_MATCH_WITH_MINOR_GAPS -> "La oferta esta cerca semanticamente y comparte habilidades directas; las brechas detectadas son menores o secundarias.";
            case TRANSFERABLE_OPPORTUNITY -> "La oferta esta cerca semanticamente y hay una ruta defendible de transferencia desde habilidades existentes.";
            case ASPIRATIONAL_MATCH -> roleAligned
                    ? "La oferta tiene afinidad de rol, pero requiere validar brechas antes de tomarla como match fuerte."
                    : "La oferta aparece cerca por significado general, aunque todavia faltan senales directas suficientes.";
            case KEYWORD_MATCH_RISK -> "Hay coincidencias de palabras clave, pero la evidencia profesional directa es debil y quedan brechas criticas.";
            case LEARNING_ROADMAP_ONLY -> "La oferta sirve mejor como mapa de aprendizaje: faltan habilidades clave para tratarla como oportunidad inmediata.";
            case LOW_FIT -> "La cercania semantica y las senales interpretables no alcanzan para defender compatibilidad profesional.";
        } + detailSuffix(matched, criticalGaps, secondaryGaps, hasTransfer);
    }

    private static String detailSuffix(int matched, int criticalGaps, int secondaryGaps, boolean hasTransfer) {
        List<String> parts = new ArrayList<>();
        if (matched > 0) {
            parts.add("matches directos: " + matched);
        }
        if (criticalGaps > 0) {
            parts.add("brechas criticas: " + criticalGaps);
        }
        if (secondaryGaps > 0) {
            parts.add("brechas secundarias: " + secondaryGaps);
        }
        if (hasTransfer) {
            parts.add("transferibilidad detectada");
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ").";
    }

    private static CompatibilityConfidence confidenceFor(
            CompatibilityCategory category,
            EvidenceLevel evidenceLevel,
            GapAnalysis gapAnalysis,
            List<TransferableSkill> transferableSkills
    ) {
        int matched = gapAnalysis == null ? 0 : gapAnalysis.directMatchCount();
        boolean hasTransfer = transferableSkills != null && !transferableSkills.isEmpty();
        boolean strongEvidence = evidenceLevel == EvidenceLevel.WORK_EXPERIENCE
                || evidenceLevel == EvidenceLevel.PROJECT
                || evidenceLevel == EvidenceLevel.CERTIFICATION;

        if ((category == CompatibilityCategory.STRONG_MATCH
                || category == CompatibilityCategory.GOOD_MATCH_WITH_MINOR_GAPS)
                && strongEvidence
                && matched >= 2) {
            return CompatibilityConfidence.HIGH;
        }
        if (category == CompatibilityCategory.TRANSFERABLE_OPPORTUNITY
                || category == CompatibilityCategory.ASPIRATIONAL_MATCH
                || matched > 0
                || hasTransfer) {
            return CompatibilityConfidence.MEDIUM;
        }
        return CompatibilityConfidence.LOW;
    }

    private static CompatibilityCategory applySeniorityDowngrade(
            CompatibilityCategory category,
            int seniorityDelta,
            int matched,
            int criticalGaps,
            boolean genericOnly
    ) {
        if (seniorityDelta < 2) {
            return category;
        }
        if (seniorityDelta >= 3 && (genericOnly || matched == 0)) {
            return CompatibilityCategory.LOW_FIT;
        }
        if (criticalGaps > 0 && matched <= 1) {
            return CompatibilityCategory.LEARNING_ROADMAP_ONLY;
        }
        return switch (category) {
            case STRONG_MATCH, GOOD_MATCH_WITH_MINOR_GAPS, TRANSFERABLE_OPPORTUNITY -> CompatibilityCategory.ASPIRATIONAL_MATCH;
            default -> category;
        };
    }

    private static boolean hasOnlyGenericMatchedSkills(GapAnalysis gapAnalysis) {
        if (gapAnalysis == null || gapAnalysis.matchedSkills() == null || gapAnalysis.matchedSkills().isEmpty()) {
            return false;
        }
        for (String skill : gapAnalysis.matchedSkills()) {
            if (!GENERIC_EVIDENCE_SKILLS.contains(normalize(skill))) {
                return false;
            }
        }
        return true;
    }

    private static int seniorityDelta(CompatibilitySignalContext context) {
        if (context == null) {
            return 0;
        }
        int jobRank = seniorityRank(context.detectedSeniority());
        int profileRank = seniorityRank(context.profileSeniority());
        if (jobRank <= 0 || profileRank <= 0) {
            return 0;
        }
        return jobRank - profileRank;
    }

    private static int seniorityRank(String seniority) {
        return switch (normalize(seniority)) {
            case "trainee" -> 1;
            case "junior" -> 2;
            case "mid", "semi_senior", "semisenior", "ssr" -> 3;
            case "senior", "sr" -> 4;
            case "lead" -> 5;
            default -> 0;
        };
    }

    private static String normalize(String value) {
        return SkillExtractionService.normalizeText(value).replace(' ', '_');
    }

    private static boolean containsAny(String normalizedText, String... phrases) {
        String haystack = " " + normalizedText + " ";
        for (String phrase : phrases) {
            String needle = " " + SkillExtractionService.normalizeText(phrase) + " ";
            if (!needle.isBlank() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
