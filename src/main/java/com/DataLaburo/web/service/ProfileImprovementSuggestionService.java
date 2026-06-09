package com.DataLaburo.web.service;

import com.DataLaburo.web.analysis.CompatibilityConfidence;
import com.DataLaburo.web.analysis.EvidenceLevel;
import com.DataLaburo.web.analysis.SkillEquivalenceSignal;
import com.DataLaburo.web.analysis.TransferableSkill;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ProfileImprovementSuggestionService {
    private static final int MAX_SUGGESTIONS = 3;

    public List<ProfileImprovementSuggestion> suggest(
            CandidateProfile profile,
            List<CandidateProfileProject> projects,
            VectorFirstCompatibilityResult result
    ) {
        if (profile == null || result == null) {
            return List.of();
        }

        EvidenceCorpus corpus = EvidenceCorpus.from(profile, projects);
        Map<String, ProfileImprovementSuggestion> suggestions = new LinkedHashMap<>();

        addCriticalGapSuggestions(suggestions, corpus, result.missingCriticalSkills());
        addSecondaryGapSuggestions(suggestions, result.missingSecondarySkills());
        addTransferSuggestions(suggestions, result.transferableSkills());
        addPartialRelationSuggestions(suggestions, result.skillEquivalenceSignals());
        addEvidenceSuggestion(suggestions, result.evidenceLevel(), result.confidence());

        return suggestions.values().stream()
                .sorted(Comparator.comparingInt(ProfileImprovementSuggestion::priority))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    public List<ProfileImprovementSuggestion> suggestProfile(CandidateProfile profile) {
        if (profile == null) {
            return List.of();
        }

        Map<String, ProfileImprovementSuggestion> suggestions = new LinkedHashMap<>();
        addMetadataSuggestion(suggestions, profile);
        addTargetRoleSuggestion(suggestions, profile);
        return suggestions.values().stream()
                .sorted(Comparator.comparingInt(ProfileImprovementSuggestion::priority))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private void addCriticalGapSuggestions(
            Map<String, ProfileImprovementSuggestion> suggestions,
            EvidenceCorpus corpus,
            List<String> missingCriticalSkills
    ) {
        for (String skill : cleanStrings(missingCriticalSkills)) {
            if (corpus.contains(skill)) {
                put(suggestions, new ProfileImprovementSuggestion(
                        "EVIDENCE",
                        "Si ya lo usaste, podrías evidenciar mejor " + skill + " en el CV o en un proyecto visible.",
                        "Aparece como faltante crítico en esta oferta, pero hay señales de esa habilidad en el perfil.",
                        10
                ));
            } else {
                put(suggestions, new ProfileImprovementSuggestion(
                        "LEARNING_GAP",
                        "Podrías reforzar " + skill + " si querés apuntar a ofertas similares.",
                        "Aparece como faltante crítico y no se detectó evidencia visible en el perfil.",
                        20
                ));
            }
        }
    }

    private void addSecondaryGapSuggestions(
            Map<String, ProfileImprovementSuggestion> suggestions,
            List<String> missingSecondarySkills
    ) {
        for (String skill : cleanStrings(missingSecondarySkills)) {
            put(suggestions, new ProfileImprovementSuggestion(
                    "LIGHT_REINFORCEMENT",
                    "Si corresponde, podrías aclarar experiencia o práctica con " + skill + ".",
                    "Aparece como faltante secundario para esta oferta.",
                    40
            ));
        }
    }

    private void addTransferSuggestions(
            Map<String, ProfileImprovementSuggestion> suggestions,
            List<TransferableSkill> transferableSkills
    ) {
        for (TransferableSkill skill : safeList(transferableSkills)) {
            if (skill == null || isBlank(skill.from()) || isBlank(skill.to())) {
                continue;
            }
            put(suggestions, new ProfileImprovementSuggestion(
                    "TRANSFER",
                    "Si corresponde, podrías explicar cómo tu experiencia en " + skill.from()
                            + " se relaciona con " + skill.to() + ".",
                    "La oferta muestra una habilidad transferible, no una habilidad presente confirmada.",
                    30
            ));
        }
    }

    private void addPartialRelationSuggestions(
            Map<String, ProfileImprovementSuggestion> suggestions,
            List<SkillEquivalenceSignal> skillEquivalenceSignals
    ) {
        for (SkillEquivalenceSignal signal : safeList(skillEquivalenceSignals)) {
            if (signal == null || isBlank(signal.candidateSkill()) || isBlank(signal.targetSkill())) {
                continue;
            }
            put(suggestions, new ProfileImprovementSuggestion(
                    "PARTIAL_RELATION",
                    "Si corresponde, podrías aclarar el contexto entre "
                            + signal.candidateSkill() + " y " + signal.targetSkill() + ".",
                    "La relación detectada es parcial o contextual y no cuenta como requisito cumplido.",
                    35
            ));
        }
    }

    private void addEvidenceSuggestion(
            Map<String, ProfileImprovementSuggestion> suggestions,
            EvidenceLevel evidenceLevel,
            CompatibilityConfidence confidence
    ) {
        boolean lowEvidence = evidenceLevel == EvidenceLevel.MENTIONED_ONLY
                || evidenceLevel == EvidenceLevel.NO_EVIDENCE;
        boolean lowConfidence = confidence == CompatibilityConfidence.LOW
                || confidence == CompatibilityConfidence.MEDIUM;
        if (!lowEvidence && !lowConfidence) {
            return;
        }

        put(suggestions, new ProfileImprovementSuggestion(
                "EVIDENCE",
                "Podrías sumar evidencia concreta en el CV o en un proyecto visible, si ya existe.",
                "El resultado tiene evidencia limitada o confianza no alta.",
                50
        ));
    }

    private void addMetadataSuggestion(
            Map<String, ProfileImprovementSuggestion> suggestions,
            CandidateProfile profile
    ) {
        List<String> missing = new ArrayList<>();
        if (isBlank(profile.getHeadline())) {
            missing.add("titulo profesional");
        }
        if (isBlank(profile.getSummary())) {
            missing.add("resumen");
        }
        if (isBlank(profile.getDeclaredSkillsText())) {
            missing.add("skills declaradas");
        }
        if (isBlank(profile.getLinkedinUrl()) && isBlank(profile.getGithubUrl()) && isBlank(profile.getPortfolioUrl())) {
            missing.add("link profesional si corresponde");
        }
        if (missing.isEmpty()) {
            return;
        }

        put(suggestions, new ProfileImprovementSuggestion(
                "PROFILE_METADATA",
                "Podrías completar " + joinHuman(missing) + " para mejorar la presentación visible del perfil.",
                "La metadata visible ayuda a interpretar el perfil, pero no cambia embeddings ni ranking.",
                60
        ));
    }

    private void addTargetRoleSuggestion(
            Map<String, ProfileImprovementSuggestion> suggestions,
            CandidateProfile profile
    ) {
        if (!"UNDECIDED".equals(cleanCode(profile.getTargetRole()))) {
            return;
        }
        put(suggestions, new ProfileImprovementSuggestion(
                "PROFILE_FOCUS",
                "Podrías definir un rol objetivo para orientar mejor la lectura del perfil.",
                "El perfil no tiene un rol objetivo declarado.",
                55
        ));
    }

    private static void put(
            Map<String, ProfileImprovementSuggestion> suggestions,
            ProfileImprovementSuggestion suggestion
    ) {
        suggestions.putIfAbsent(suggestion.category() + ":" + suggestion.message(), suggestion);
    }

    private static List<String> cleanStrings(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String cleanCode(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String joinHuman(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " y " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " y " + values.get(values.size() - 1);
    }

    private record EvidenceCorpus(String normalizedText) {
        static EvidenceCorpus from(CandidateProfile profile, List<CandidateProfileProject> projects) {
            StringBuilder text = new StringBuilder();
            append(text, profile.getCvText());
            append(text, profile.getDeclaredSkillsText());
            for (CandidateProfileProject project : safeList(projects)) {
                if (project == null) {
                    continue;
                }
                append(text, project.getTitle());
                append(text, project.getDescription());
                append(text, project.getSkillsText());
            }
            return new EvidenceCorpus(normalize(text.toString()));
        }

        boolean contains(String skill) {
            String normalizedSkill = normalize(skill);
            if (normalizedSkill.isBlank() || normalizedText.isBlank()) {
                return false;
            }
            if (normalizedSkill.matches("[a-z0-9 ]+")) {
                Pattern pattern = Pattern.compile("(^|[^a-z0-9])"
                        + Pattern.quote(normalizedSkill)
                        + "($|[^a-z0-9])");
                return pattern.matcher(normalizedText).find();
            }
            return normalizedText.contains(normalizedSkill);
        }

        private static void append(StringBuilder text, String value) {
            if (value != null && !value.isBlank()) {
                text.append(' ').append(value);
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record ProfileImprovementSuggestion(
            String category,
            String message,
            String reason,
            int priority
    ) {
    }
}
