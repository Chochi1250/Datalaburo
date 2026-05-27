package com.DataLaburo.web.analysis;

import com.DataLaburo.web.service.SkillExtractionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TransferabilityService {
    private static final List<TransferRule> RULES = List.of(
            rule("Java", "C#", TransferStrength.PARTIAL, "base orientada a objetos transferible"),
            rule("C", "C++", TransferStrength.PARTIAL, "base de sintaxis y memoria transferible"),
            rule("SQL", "PostgreSQL", TransferStrength.STRONG, "fundamentos SQL transferibles a PostgreSQL"),
            rule("SQL", "MySQL", TransferStrength.STRONG, "fundamentos SQL transferibles a MySQL"),
            rule("SQL", "SQL Server", TransferStrength.STRONG, "fundamentos SQL transferibles a SQL Server"),
            rule("Docker", "Kubernetes", TransferStrength.PARTIAL, "base de contenedores transferible"),
            rule("Spring Boot", "Backend frameworks", TransferStrength.PARTIAL, "patrones backend transferibles a otros frameworks"),
            rule("Backend", "Cloud", TransferStrength.PARTIAL, "experiencia backend ayuda a operar servicios en cloud"),
            rule("Backend", "DevOps", TransferStrength.PARTIAL, "conocimiento de servicios backend ayuda en practicas DevOps"),
            rule("Technical Support", "IT Analyst", TransferStrength.PARTIAL, "diagnostico y soporte son base para analisis IT"),
            rule("Technical Support", "Cloud Support", TransferStrength.PARTIAL, "soporte tecnico se puede transferir a soporte cloud"),
            rule("IT Support", "IT Analyst", TransferStrength.PARTIAL, "diagnostico y soporte son base para analisis IT"),
            rule("IT Support", "Cloud Support", TransferStrength.PARTIAL, "soporte tecnico se puede transferir a soporte cloud")
    );

    private static final Map<String, String> CANONICAL_SIGNAL_BY_NORMALIZED = Map.ofEntries(
            Map.entry("backend", "Backend"),
            Map.entry("backend development", "Backend"),
            Map.entry("backend developer", "Backend"),
            Map.entry("backend frameworks", "Backend frameworks"),
            Map.entry("backend framework", "Backend frameworks"),
            Map.entry("backend web", "Backend"),
            Map.entry("cloud", "Cloud"),
            Map.entry("devops", "DevOps"),
            Map.entry("dev ops", "DevOps"),
            Map.entry("technical support", "Technical Support"),
            Map.entry("it support", "IT Support"),
            Map.entry("cloud support", "Cloud Support"),
            Map.entry("it analyst", "IT Analyst"),
            Map.entry("java", "Java"),
            Map.entry("c#", "C#"),
            Map.entry("c", "C"),
            Map.entry("c++", "C++"),
            Map.entry("sql", "SQL"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("mysql", "MySQL"),
            Map.entry("sql server", "SQL Server"),
            Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("k8s", "Kubernetes"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("springboot", "Spring Boot"),
            Map.entry("spring", "Spring Boot")
    );

    public List<TransferableSkill> findTransferableSkills(
            Collection<String> candidateSignals,
            Collection<String> targetSignals
    ) {
        Set<String> candidateCanonical = canonicalizeAll(candidateSignals);
        Set<String> targetCanonical = canonicalizeAll(targetSignals);
        Map<String, TransferableSkill> out = new LinkedHashMap<>();

        for (TransferRule rule : RULES) {
            String fromCanonical = canonicalLabel(rule.from());
            String toCanonical = canonicalLabel(rule.to());
            if (!candidateCanonical.contains(fromCanonical)) {
                continue;
            }
            if (!targetCanonical.contains(toCanonical)) {
                continue;
            }
            String key = fromCanonical + "->" + toCanonical;
            out.putIfAbsent(key, new TransferableSkill(
                    fromCanonical,
                    toCanonical,
                    rule.strength(),
                    rule.reason()
            ));
        }

        return out.values().stream()
                .limit(8)
                .toList();
    }

    private static Set<String> canonicalizeAll(Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            String canonical = canonicalLabel(value);
            if (!canonical.isBlank()) {
                out.add(canonical);
            }
        }
        return out;
    }

    private static String canonicalLabel(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        String canonical = CANONICAL_SIGNAL_BY_NORMALIZED.get(normalized);
        return canonical != null ? canonical : value.trim();
    }

    private static String normalize(String value) {
        return SkillExtractionService.normalizeText(value);
    }

    private static TransferRule rule(String from, String to, TransferStrength strength, String reason) {
        return new TransferRule(
                from,
                to,
                strength,
                reason
        );
    }

    private record TransferRule(
            String from,
            String to,
            TransferStrength strength,
            String reason
    ) {
        private TransferRule {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            Objects.requireNonNull(strength);
            Objects.requireNonNull(reason);
        }
    }
}
