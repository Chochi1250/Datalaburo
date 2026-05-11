package com.DataLaburo.web.service;

import com.DataLaburo.web.model.Skill;
import com.DataLaburo.web.model.SkillAlias;
import com.DataLaburo.web.repository.SkillAliasRepository;
import com.DataLaburo.web.repository.SkillRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SkillExtractionService {
	private final SkillRepository skillRepository;
	private final SkillAliasRepository skillAliasRepository;

	public SkillExtractionService(SkillRepository skillRepository, SkillAliasRepository skillAliasRepository) {
		this.skillRepository = skillRepository;
		this.skillAliasRepository = skillAliasRepository;
	}

	public SkillCatalog loadCatalog() {
		Map<Long, String> skillIdToName = new LinkedHashMap<>();
		Map<String, Long> aliasToSkillId = new LinkedHashMap<>();

		List<Skill> skills = skillRepository.findAll();
		for (Skill s : skills) {
			if (!s.isEnabled()) continue;
			skillIdToName.put(s.getId(), s.getName());
			String key = s.getNameNormalized();
			if (key != null && !key.isBlank()) {
				aliasToSkillId.putIfAbsent(key, s.getId());
			}
		}

		List<SkillAlias> aliases = skillAliasRepository.findAllWithEnabledSkill();
		for (SkillAlias alias : aliases) {
			String key = alias.getAliasNormalized();
			if (key != null && !key.isBlank()) {
				aliasToSkillId.putIfAbsent(key, alias.getSkill().getId());
			}
		}

		return new SkillCatalog(aliasToSkillId, skillIdToName);
	}

	public ExtractedSkills extractSkills(String rawText) {
		return extractSkills(rawText, loadCatalog());
	}

	public ExtractedSkills extractSkills(String rawText, SkillCatalog catalog) {
		String normalizedText = normalizeText(rawText);
		if (normalizedText.isBlank()) {
			return new ExtractedSkills(Set.of(), Map.of());
		}

		String paddedHaystack = " " + normalizedText + " ";

		Set<Long> found = new LinkedHashSet<>();
		for (Map.Entry<String, Long> entry : catalog.aliasToSkillId().entrySet()) {
			String aliasNorm = entry.getKey();
			if (aliasNorm.isBlank()) continue;

			String needle = " " + aliasNorm + " ";
			if (paddedHaystack.contains(needle)) {
				found.add(entry.getValue());
			}
		}

		Map<Long, String> foundNames = new LinkedHashMap<>();
		for (Long id : found) {
			String name = catalog.skillIdToName().get(id);
			if (name != null) {
				foundNames.put(id, name);
			}
		}

		return new ExtractedSkills(found, foundNames);
	}

	public static String normalizeText(String s) {
		// Reuse the skill-name normalization for full text (it already removes accents and normalizes spaces).
		// Keep it simple: same rules for CV and job text.
		return Skill.normalizeName(s).toLowerCase(Locale.ROOT);
	}

	public record SkillCatalog(Map<String, Long> aliasToSkillId, Map<Long, String> skillIdToName) {}
	public record ExtractedSkills(Set<Long> skillIds, Map<Long, String> skillIdToName) {}
}
