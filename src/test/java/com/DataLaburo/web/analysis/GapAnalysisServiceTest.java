package com.DataLaburo.web.analysis;

import com.DataLaburo.web.service.SkillExtractionService;
import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapAnalysisServiceTest {
    private final SkillExtractionService skillExtractionService = new SkillExtractionService(null, null);
    private final GapAnalysisService service = new GapAnalysisService(skillExtractionService);

    @Test
    void separatesCriticalAndSecondaryGaps() {
        GapAnalysis analysis = service.analyze(
                List.of("Java", "SQL"),
                List.of("Java", "Spring Boot"),
                List.of("Docker", "SQL")
        );

        assertEquals(List.of("Java", "SQL"), analysis.matchedSkills());
        assertEquals(List.of("Spring Boot"), analysis.missingCriticalSkills());
        assertEquals(List.of("Docker"), analysis.missingSecondarySkills());
    }

    @Test
    void doesNotMarkElasticsearchCriticalWhenItIsNotPresentInJob() {
        Job job = job(
                "Backend Engineer",
                "Requisitos: Java y SQL.",
                "El rol trabaja con APIs."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con Java",
                extracted(2L, "Java"),
                job,
                elasticCatalog()
        );

        assertFalse(analysis.missingCriticalSkills().contains("Elasticsearch"));
        assertFalse(analysis.missingSecondarySkills().contains("Elasticsearch"));
    }

    @Test
    void doesNotPromoteWeakShortAliasToCriticalSkill() {
        Job job = job(
                "Soporte tecnico",
                "El puesto es remoto y requiere SQL.",
                "La comunicacion es importante."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con SQL",
                extracted(3L, "SQL"),
                job,
                elasticCatalog()
        );

        assertFalse(analysis.missingCriticalSkills().contains("Elasticsearch"));
        assertFalse(analysis.missingSecondarySkills().contains("Elasticsearch"));
    }

    @Test
    void keepsElasticsearchCriticalWhenExplicitlyRequired() {
        Job job = job(
                "Backend Engineer",
                "Requisitos: Java, SQL y Elasticsearch.",
                "Experiencia en busquedas y observabilidad."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con Java y SQL",
                extracted(2L, "Java", 3L, "SQL"),
                job,
                elasticCatalog()
        );

        assertEquals(List.of("Elasticsearch"), analysis.missingCriticalSkills());
        assertTrue(analysis.criticalEvidence().stream()
                .anyMatch(evidence -> evidence.skillName().equals("Elasticsearch")
                        && evidence.strength() == SkillEvidenceStrength.STRONG));
    }

    private static Job job(String title, String requirements, String description) {
        Job job = new Job();
        job.setId(101L);
        job.setTitle(title);
        job.setRequirementsText(requirements);
        job.setDescription(description);
        job.setSourceUrl("https://example.test/jobs/101");
        return job;
    }

    private static SkillExtractionService.ExtractedSkills extracted(Object... idAndName) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        Map<Long, String> names = new java.util.LinkedHashMap<>();
        for (int i = 0; i < idAndName.length; i += 2) {
            Long id = (Long) idAndName[i];
            String name = (String) idAndName[i + 1];
            ids.add(id);
            names.put(id, name);
        }
        return new SkillExtractionService.ExtractedSkills(ids, names);
    }

    private static SkillExtractionService.SkillCatalog elasticCatalog() {
        Map<String, Long> aliases = new java.util.LinkedHashMap<>();
        aliases.put("elasticsearch", 1L);
        aliases.put("elastic search", 1L);
        aliases.put("es", 1L);
        aliases.put("java", 2L);
        aliases.put("sql", 3L);

        Map<Long, String> names = new java.util.LinkedHashMap<>();
        names.put(1L, "Elasticsearch");
        names.put(2L, "Java");
        names.put(3L, "SQL");
        return new SkillExtractionService.SkillCatalog(aliases, names);
    }
}
