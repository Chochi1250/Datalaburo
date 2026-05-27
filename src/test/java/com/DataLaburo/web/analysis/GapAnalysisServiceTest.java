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

    @Test
    void satisfiedGoOrJavaAlternativeDoesNotMarkGoAsCriticalGap() {
        Job job = job(
                "Backend Engineer",
                "Requisitos: experiencia desarrollando servicios con Go o Java.",
                "Diseno de APIs REST."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con Java y APIs REST",
                extracted(2L, "Java"),
                job,
                skillCatalog("Go", "Java", "REST")
        );

        assertFalse(analysis.missingCriticalSkills().contains("Go"));
        assertTrue(analysis.matchedSkills().contains("Java"));
    }

    @Test
    void satisfiedDatabaseEngineAlternativeDoesNotMarkAllEnginesCritical() {
        Job job = job(
                "Database Developer",
                "Requisitos: PostgreSQL/Oracle/SQL Server.",
                "Optimizacion de queries."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con PostgreSQL y SQL",
                extracted(1L, "PostgreSQL", 2L, "SQL"),
                job,
                skillCatalog("PostgreSQL", "SQL")
        );

        assertFalse(analysis.missingCriticalSkills().contains("Oracle"));
        assertFalse(analysis.missingCriticalSkills().contains("SQL Server"));
        assertTrue(analysis.matchedSkills().contains("PostgreSQL"));
    }

    @Test
    void dotNetInLateralDomainNoiseIsNotCritical() {
        Job job = job(
                "IAM Engineer",
                "Mandatory Skills: IAM, OAuth, SAML, SQL.",
                "Company insights from growthinvesting.net and market data."
        );

        GapAnalysis analysis = service.analyze(
                "Experiencia con SQL",
                extracted(4L, "SQL"),
                job,
                skillCatalog("IAM", "OAuth", "SAML", "SQL", ".NET")
        );

        assertFalse(analysis.missingCriticalSkills().contains(".NET"));
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
        return skillCatalog("Elasticsearch", "Java", "SQL");
    }

    private static SkillExtractionService.SkillCatalog skillCatalog(String... skills) {
        Map<String, Long> aliases = new java.util.LinkedHashMap<>();
        Map<Long, String> names = new java.util.LinkedHashMap<>();
        long id = 1L;
        for (String skill : skills) {
            names.put(id, skill);
            aliases.put(SkillExtractionService.normalizeText(skill), id);
            if ("Elasticsearch".equals(skill)) {
                aliases.put("elastic search", id);
                aliases.put("es", id);
            }
            id++;
        }
        return new SkillExtractionService.SkillCatalog(aliases, names);
    }
}
