package com.DataLaburo.web.service;

import com.DataLaburo.web.service.RuleBasedEnrichmentService.EnrichedDocument;
import com.DataLaburo.web.service.RuleBasedEnrichmentService.Seniority;
import com.DataLaburo.web.service.SkillExtractionService.ExtractedSkills;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedEnrichmentServiceTest {
	private final RuleBasedEnrichmentService service = new RuleBasedEnrichmentService();
	private final ExtractedSkills noSkills = new ExtractedSkills(Set.of(), Map.of());

	@Test
	void traineeSignalWinsOverAmbiguousLeadWordsAndYears() {
		EnrichedDocument doc = service.enrichJob(
				"Programa Trainees Edenor 2026. Funciones principales del puesto. 10 anos de trayectoria en la red.",
				noSkills
		);

		assertEquals(Seniority.TRAINEE, doc.seniority());
	}

	@Test
	void detectsExplicitLeadRoles() {
		assertEquals(Seniority.LEAD, seniority("Tech Lead Java"));
		assertEquals(Seniority.LEAD, seniority("Team Lead Backend"));
		assertEquals(Seniority.LEAD, seniority("Principal Engineer"));
	}

	@Test
	void principalAloneDoesNotMeanLead() {
		assertNull(seniority("Funciones principales del puesto"));
	}

	@Test
	void keepsJuniorAndSeniorCompatibility() {
		assertEquals(Seniority.JUNIOR, seniority("Desarrollador Junior Java"));
		assertEquals(Seniority.SENIOR, seniority("Senior Backend Developer"));
	}

	private Seniority seniority(String text) {
		return service.enrichJob(text, noSkills).seniority();
	}
}
