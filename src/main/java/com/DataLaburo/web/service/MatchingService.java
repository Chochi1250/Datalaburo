package com.DataLaburo.web.service;

import com.DataLaburo.web.dto.CandidateProfileForm;
import com.DataLaburo.web.dto.MatchResultView;
import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchingService {
	private static final Map<String, String> SKILL_SYNONYMS = Map.ofEntries(
			Map.entry("js", "javascript"),
			Map.entry("ts", "typescript"),
			Map.entry("spring", "spring boot"),
			Map.entry("k8s", "kubernetes"),
			Map.entry("postgres", "postgresql"),
			Map.entry("ci", "ci/cd"),
			Map.entry("cd", "ci/cd")
	);

	public MatchResultView match(CandidateProfileForm form, Job job) {
		Set<String> candidateSkills = extractCandidateSkills(form);

		// TEMPORARY: requirements extraction is disabled (too fragile). Match only against description/visible text.
		String jobText = firstNonBlank(job.getDescription(), job.getVisibleText());
		if (candidateSkills.isEmpty()) {
			return new MatchResultView(
					0,
					List.of(),
					List.of(),
					List.of(),
					List.of("No skills provided. Paste 5–20 skills (comma or newline separated) and try again.")
			);
		}

		if (jobText == null || jobText.isBlank()) {
			return new MatchResultView(
					0,
					List.of(),
					List.of(),
					List.of(),
					List.of("This job has no description/requirements yet. Capture it again with the plugin so JOBS gets description text.")
			);
		}

		String normalizedJobText = normalizeForContains(jobText);

		Set<String> matchedCandidateSkills = new LinkedHashSet<>();
		for (String skill : candidateSkills) {
			String needle = normalizeForContains(skill);
			if (!needle.isBlank() && normalizedJobText.contains(needle)) {
				matchedCandidateSkills.add(skill);
			}
		}

		int score = (int) Math.round(100.0 * ((double) matchedCandidateSkills.size() / (double) candidateSkills.size()));
		score = Math.max(0, Math.min(100, score));

		Set<String> missingFromCandidate = subtract(candidateSkills, matchedCandidateSkills);

		List<String> feedback = new ArrayList<>();
		feedback.add("Tip: this scoring is keyword-based (MVP). If the job text uses different wording, try adding synonyms (e.g., 'js' and 'javascript').");

		return new MatchResultView(
				score,
				matchedCandidateSkills.stream().sorted(Comparator.naturalOrder()).toList(),
				missingFromCandidate.stream().sorted(Comparator.naturalOrder()).toList(),
				List.of(),
				feedback
		);
	}

	private static Set<String> extractCandidateSkills(CandidateProfileForm form) {
		Set<String> skills = new LinkedHashSet<>();
		addFromSkillsText(skills, form.getSkillsText());
		return skills.stream()
				.map(MatchingService::canonicalSkill)
				.filter(s -> !s.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static void addFromSkillsText(Set<String> into, String skillsText) {
		if (skillsText == null || skillsText.isBlank()) {
			return;
		}
		String normalized = skillsText.replace("\r", "\n");
		for (String token : normalized.split("[,\\n;]")) {
			String s = token == null ? "" : token.trim();
			if (!s.isEmpty()) {
				into.add(s);
			}
		}
	}

	private static Set<String> subtract(Set<String> from, Set<String> remove) {
		Set<String> out = new LinkedHashSet<>();
		for (String s : from) {
			if (!remove.contains(s)) {
				out.add(s);
			}
		}
		return out;
	}

	private static String canonicalSkill(String skill) {
		String s = normalize(skill);
		return SKILL_SYNONYMS.getOrDefault(s, s);
	}

	private static String normalize(String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeForContains(String s) {
		if (s == null) {
			return "";
		}
		String lower = s.toLowerCase(Locale.ROOT);
		// Basic normalization: keep letters/numbers, convert the rest to spaces.
		String cleaned = lower.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ");
		return cleaned.trim().replaceAll("\\s+", " ");
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a;
		}
		return (b != null && !b.isBlank()) ? b : null;
	}
}
