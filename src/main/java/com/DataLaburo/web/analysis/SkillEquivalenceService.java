package com.DataLaburo.web.analysis;

import com.DataLaburo.web.service.SkillExtractionService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SkillEquivalenceService {
    private static final List<SkillRelationRule> RULES = List.of(
            rule("PostgreSQL", "SQL", "PARTIAL_EQUIVALENCE", "PostgreSQL aporta fundamentos SQL core, aunque no cubre todo el contexto de la oferta."),
            rule("MySQL", "SQL", "PARTIAL_EQUIVALENCE", "MySQL aporta fundamentos SQL core, aunque no cubre todo el contexto de la oferta."),
            rule("SQL Server", "SQL", "PARTIAL_EQUIVALENCE", "SQL Server aporta fundamentos SQL core, aunque no cubre todo el contexto de la oferta."),
            rule("AWS", "Cloud", "PARTIAL_TRANSFER", "Experiencia en AWS es transferible parcialmente a roles cloud generales."),
            rule("Azure", "Cloud", "PARTIAL_TRANSFER", "Experiencia en Azure es transferible parcialmente a roles cloud generales."),
            rule("GCP", "Cloud", "PARTIAL_TRANSFER", "Experiencia en GCP es transferible parcialmente a roles cloud generales."),
            rule("ITIL", "ITSM", "RELATED", "ITIL se relaciona con practicas ITSM, pero no reemplaza evidencia operativa especifica."),
            rule("ITSM", "ITIL", "RELATED", "ITSM se relaciona con ITIL, pero no reemplaza certificacion o practica ITIL explicita."),
            rule("Spring Boot", "Java Backend", "CONTEXTUAL", "Spring Boot suele aportar contexto de backend Java."),
            rule("Spring Boot", "REST API", "CONTEXTUAL", "Spring Boot suele usarse para construir APIs REST."),
            rule("Spring Boot", "MVC", "CONTEXTUAL", "Spring Boot puede cubrir aplicaciones web MVC segun el proyecto."),
            rule("Spring Boot", "Microservices", "CONTEXTUAL", "Spring Boot puede transferirse a microservicios cuando hay contexto de servicios backend.")
    );

    private static final Map<String, String> CANONICAL_BY_NORMALIZED = Map.ofEntries(
            Map.entry("sql", "SQL"),
            Map.entry("sql core", "SQL"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("postgre sql", "PostgreSQL"),
            Map.entry("mysql", "MySQL"),
            Map.entry("sql server", "SQL Server"),
            Map.entry("mssql", "SQL Server"),
            Map.entry("ms sql", "SQL Server"),
            Map.entry("microsoft sql server", "SQL Server"),
            Map.entry("cloud", "Cloud"),
            Map.entry("cloud computing", "Cloud"),
            Map.entry("aws", "AWS"),
            Map.entry("amazon web services", "AWS"),
            Map.entry("azure", "Azure"),
            Map.entry("microsoft azure", "Azure"),
            Map.entry("gcp", "GCP"),
            Map.entry("google cloud", "GCP"),
            Map.entry("google cloud platform", "GCP"),
            Map.entry("itil", "ITIL"),
            Map.entry("itsm", "ITSM"),
            Map.entry("it service management", "ITSM"),
            Map.entry("spring", "Spring Boot"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("springboot", "Spring Boot"),
            Map.entry("backend", "Java Backend"),
            Map.entry("backend java", "Java Backend"),
            Map.entry("java backend", "Java Backend"),
            Map.entry("java backend developer", "Java Backend"),
            Map.entry("rest", "REST API"),
            Map.entry("rest api", "REST API"),
            Map.entry("rest apis", "REST API"),
            Map.entry("api rest", "REST API"),
            Map.entry("apis rest", "REST API"),
            Map.entry("mvc", "MVC"),
            Map.entry("spring mvc", "MVC"),
            Map.entry("microservice", "Microservices"),
            Map.entry("microservices", "Microservices"),
            Map.entry("microservicios", "Microservices"),
            Map.entry("microservicio", "Microservices")
    );

    public List<SkillEquivalenceSignal> findSignals(
            Collection<String> candidateSkills,
            Collection<String> targetMissingSkills
    ) {
        Map<String, String> candidateDisplayByCanonical = canonicalDisplayMap(candidateSkills);
        Map<String, String> targetDisplayByCanonical = canonicalDisplayMap(targetMissingSkills);
        Map<String, SkillEquivalenceSignal> out = new LinkedHashMap<>();

        for (SkillRelationRule rule : RULES) {
            String candidateDisplay = candidateDisplayByCanonical.get(rule.from());
            String targetDisplay = targetDisplayByCanonical.get(rule.to());
            if (candidateDisplay == null || targetDisplay == null) {
                continue;
            }
            String key = rule.from() + "->" + rule.to();
            out.putIfAbsent(key, new SkillEquivalenceSignal(
                    candidateDisplay,
                    targetDisplay,
                    rule.relation(),
                    rule.reason()
            ));
        }

        return out.values().stream()
                .limit(8)
                .toList();
    }

    private static Map<String, String> canonicalDisplayMap(Collection<String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String value : safeValues(values)) {
            String canonical = canonicalLabel(value);
            if (!canonical.isBlank()) {
                out.putIfAbsent(canonical, value.trim());
            }
        }
        return out;
    }

    private static Set<String> safeValues(Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    private static String canonicalLabel(String value) {
        String normalized = SkillExtractionService.normalizeText(value);
        if (normalized.isBlank()) {
            return "";
        }
        return CANONICAL_BY_NORMALIZED.getOrDefault(normalized, value.trim());
    }

    private static SkillRelationRule rule(String from, String to, String relation, String reason) {
        return new SkillRelationRule(from, to, relation, reason);
    }

    private record SkillRelationRule(
            String from,
            String to,
            String relation,
            String reason
    ) {
        private SkillRelationRule {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            Objects.requireNonNull(relation);
            Objects.requireNonNull(reason);
        }
    }
}
