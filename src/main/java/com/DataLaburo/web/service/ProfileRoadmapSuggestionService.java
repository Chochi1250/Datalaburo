package com.DataLaburo.web.service;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProfileRoadmapSuggestionService {
    private static final int MAX_ROADMAPS = 3;
    private static final List<RoadmapTemplate> TEMPLATES = List.of(
            new RoadmapTemplate(
                    "SQL/PostgreSQL",
                    List.of("sql", "postgresql", "postgres", "mysql", "database", "base de datos", "queries"),
                    "Fortalecer SQL/PostgreSQL",
                    "Aparece de forma repetida en ofertas cercanas como base para trabajar con datos y persistencia.",
                    List.of(
                            "Repasar consultas SELECT, filtros, agregaciones y joins.",
                            "Practicar modelado basico de tablas y relaciones.",
                            "Revisar indices simples y lectura de planes basicos.",
                            "Aplicarlo en una API o proyecto con persistencia real."
                    ),
                    List.of(
                            "Documentar consultas o decisiones de modelado en un proyecto.",
                            "Agregar al perfil un proyecto con PostgreSQL si ya lo tenes."
                    ),
                    "Base tecnica"
            ),
            new RoadmapTemplate(
                    "Docker",
                    List.of("docker", "container", "containers", "contenedores", "docker compose", "compose"),
                    "Practicar Docker",
                    "Aparece de forma repetida en ofertas cercanas para empaquetar y ejecutar aplicaciones de forma reproducible.",
                    List.of(
                            "Reforzar conceptos de imagen, contenedor, puertos y volumenes.",
                            "Crear un Dockerfile para una API simple.",
                            "Usar variables de entorno de forma explicita.",
                            "Levantar app y base de datos con Docker Compose."
                    ),
                    List.of(
                            "Sumar un README con comandos para ejecutar el proyecto.",
                            "Publicar un repo con Dockerfile o compose si corresponde."
                    ),
                    "Practica tecnica"
            ),
            new RoadmapTemplate(
                    "Kubernetes",
                    List.of("kubernetes", "k8s", "pods", "deployments", "services", "helm"),
                    "Explorar Kubernetes paso a paso",
                    "Aparece de forma repetida en ofertas cercanas como despliegue y operacion de aplicaciones.",
                    List.of(
                            "Reforzar Docker y conceptos de contenedores.",
                            "Aprender pods, deployments y services.",
                            "Desplegar una API simple en un entorno local o de practica.",
                            "Documentar que se desplego y como se valido."
                    ),
                    List.of(
                            "Agregar manifiestos simples al repositorio del proyecto.",
                            "Explicar en el CV la practica realizada, si ya existe."
                    ),
                    "Aprendizaje inicial"
            ),
            new RoadmapTemplate(
                    "Cloud",
                    List.of("cloud", "aws", "azure", "gcp", "google cloud", "ec2", "s3", "cloud run", "app service"),
                    "Construir base practica de Cloud",
                    "Aparece de forma repetida en ofertas cercanas para desplegar y operar soluciones fuera del entorno local.",
                    List.of(
                            "Elegir un proveedor o entorno de practica.",
                            "Desplegar una app simple con variables de configuracion.",
                            "Conectar una base de datos gestionada o externa.",
                            "Revisar logs, monitoreo basico y costos de prueba."
                    ),
                    List.of(
                            "Documentar arquitectura minima y decisiones de despliegue.",
                            "Agregar link o repo del proyecto si corresponde."
                    ),
                    "Orientacion practica"
            ),
            new RoadmapTemplate(
                    "Kafka",
                    List.of("kafka", "event streaming", "mensajeria", "producer", "consumer"),
                    "Introducir mensajeria con Kafka",
                    "Aparece de forma repetida en ofertas cercanas para integrar sistemas mediante eventos.",
                    List.of(
                            "Repasar conceptos de topics, producers y consumers.",
                            "Crear un flujo simple de publicacion y consumo.",
                            "Integrarlo con una API o proceso pequeno.",
                            "Registrar errores y decisiones de diseno."
                    ),
                    List.of(
                            "Mostrar un caso de uso simple en un repo.",
                            "Explicar que problema resuelve la mensajeria en el proyecto."
                    ),
                    "Aprendizaje aplicado"
            ),
            new RoadmapTemplate(
                    "Spring Boot",
                    List.of("spring boot", "spring", "springboot", "spring mvc", "spring data", "java spring"),
                    "Reforzar Spring Boot",
                    "Aparece de forma repetida en ofertas cercanas para construir servicios backend mantenibles.",
                    List.of(
                            "Construir endpoints REST con estructura clara.",
                            "Agregar validaciones y manejo de errores.",
                            "Persistir datos con Spring Data o equivalente.",
                            "Cubrir casos principales con tests."
                    ),
                    List.of(
                            "Documentar endpoints y decisiones del proyecto.",
                            "Agregar evidencia concreta de validaciones, persistencia y tests."
                    ),
                    "Backend"
            ),
            new RoadmapTemplate(
                    "REST APIs",
                    List.of("rest", "rest api", "rest APIs", "api rest", "apis", "endpoint", "endpoints", "http"),
                    "Mejorar diseno de REST APIs",
                    "Aparece de forma repetida en ofertas cercanas para exponer funcionalidades de backend.",
                    List.of(
                            "Disenar recursos, metodos HTTP y codigos de respuesta.",
                            "Agregar validaciones y errores consistentes.",
                            "Practicar paginacion o filtros simples.",
                            "Documentar endpoints y ejemplos de uso."
                    ),
                    List.of(
                            "Incluir capturas, README o coleccion de requests si corresponde.",
                            "Describir el criterio de diseno de la API en el perfil."
                    ),
                    "Backend"
            ),
            new RoadmapTemplate(
                    "Testing",
                    List.of("testing", "tests", "unit tests", "integration tests", "junit", "mockito", "qa", "test"),
                    "Sumar practica de testing",
                    "Aparece de forma repetida en ofertas cercanas como senal de calidad y mantenibilidad.",
                    List.of(
                            "Cubrir logica principal con tests unitarios.",
                            "Agregar tests de integracion para endpoints o persistencia.",
                            "Practicar mocks solo donde aporten claridad.",
                            "Documentar que casos quedan cubiertos."
                    ),
                    List.of(
                            "Mostrar cobertura de casos relevantes en el README.",
                            "Mencionar tipos de tests usados si ya los aplicaste."
                    ),
                    "Calidad"
            ),
            new RoadmapTemplate(
                    "Frontend basico",
                    List.of("frontend", "front-end", "html", "css", "javascript", "react", "angular", "vue"),
                    "Cubrir frontend basico",
                    "Aparece de forma repetida en ofertas cercanas cuando se espera interaccion con interfaces o consumo de APIs.",
                    List.of(
                            "Repasar HTML, CSS y JavaScript basico.",
                            "Consumir una API simple desde una pantalla.",
                            "Manejar estados de carga y errores.",
                            "Documentar el flujo principal de usuario."
                    ),
                    List.of(
                            "Agregar una UI minima a un proyecto backend si corresponde.",
                            "Mostrar capturas o link de demo cuando exista."
                    ),
                    "Complemento visual"
            ),
            new RoadmapTemplate(
                    "Soporte/App Support",
                    List.of("soporte", "support", "app support", "application support", "itil", "itsm", "troubleshooting", "logs", "cybersecurity", "ciberseguridad", "security"),
                    "Ordenar evidencia de soporte y operacion",
                    "Aparece de forma repetida en ofertas cercanas relacionadas con diagnostico, operacion y soporte de aplicaciones.",
                    List.of(
                            "Practicar analisis de logs y reproduccion de incidentes.",
                            "Ordenar pasos de troubleshooting y comunicacion.",
                            "Revisar fundamentos de ITIL/ITSM si corresponde.",
                            "Sumar bases de seguridad operativa para casos simples."
                    ),
                    List.of(
                            "Describir incidentes o practicas sin exponer datos sensibles.",
                            "Agregar evidencia de herramientas o procedimientos usados."
                    ),
                    "Operacion"
            )
    );

    public List<ProfileRoadmapSuggestion> suggest(
            CandidateProfile profile,
            List<CandidateProfileProject> projects,
            List<VectorFirstCompatibilityResult> results
    ) {
        if (profile == null || results == null || results.isEmpty()) {
            return List.of();
        }

        EvidenceCorpus corpus = EvidenceCorpus.from(profile, projects);
        Map<String, GapAggregate> aggregates = new LinkedHashMap<>();
        Set<String> matchedFamilies = new LinkedHashSet<>();
        ProfileContext context = ProfileContext.from(profile, corpus);

        for (VectorFirstCompatibilityResult result : results) {
            if (result == null) {
                continue;
            }
            collectMatchedFamilies(matchedFamilies, result.matchedSkills());
            collectGaps(aggregates, result.missingCriticalSkills(), true);
            collectGaps(aggregates, result.missingSecondarySkills(), false);
            collectRoadmapSignals(aggregates, result.roadmapSuggestions());
            collectTransferSignals(aggregates, result.transferableSkills());
            collectEquivalenceSignals(aggregates, result.skillEquivalenceSignals());
        }

        return aggregates.values().stream()
                .filter(aggregate -> !matchedFamilies.contains(aggregate.template.skillOrFamily()))
                .filter(GapAggregate::hasRepeatedGap)
                .sorted(Comparator
                        .comparingInt((GapAggregate aggregate) -> alignmentPriority(aggregate, context))
                        .thenComparing(Comparator.comparingInt(GapAggregate::criticalResults).reversed())
                        .thenComparing(Comparator.comparingInt(GapAggregate::secondaryResults).reversed())
                        .thenComparing(GapAggregate::skillOrFamily))
                .limit(MAX_ROADMAPS)
                .map(aggregate -> toSuggestion(aggregate, context, corpus))
                .toList();
    }

    private static void collectMatchedFamilies(Set<String> matchedFamilies, List<String> skills) {
        for (String skill : safeList(skills)) {
            findTemplate(skill).ifPresent(template -> matchedFamilies.add(template.skillOrFamily()));
        }
    }

    private static void collectGaps(
            Map<String, GapAggregate> aggregates,
            List<String> skills,
            boolean critical
    ) {
        Set<String> seenInResult = new LinkedHashSet<>();
        for (String skill : safeList(skills)) {
            findTemplate(skill).ifPresent(template -> {
                if (seenInResult.add(template.skillOrFamily())) {
                    GapAggregate aggregate = aggregates.computeIfAbsent(
                            template.skillOrFamily(),
                            ignored -> new GapAggregate(template)
                    );
                    aggregate.addGap(critical);
                }
            });
        }
    }

    private static void collectRoadmapSignals(Map<String, GapAggregate> aggregates, List<String> suggestions) {
        for (String suggestion : safeList(suggestions)) {
            findTemplate(suggestion).ifPresent(template -> aggregates
                    .computeIfAbsent(template.skillOrFamily(), ignored -> new GapAggregate(template))
                    .addRelatedSignal("Sugerencia previa relacionada"));
        }
    }

    private static void collectTransferSignals(Map<String, GapAggregate> aggregates, List<TransferableSkill> skills) {
        for (TransferableSkill skill : safeList(skills)) {
            if (skill == null) {
                continue;
            }
            findTemplate(skill.to()).ifPresent(template -> aggregates
                    .computeIfAbsent(template.skillOrFamily(), ignored -> new GapAggregate(template))
                    .addRelatedSignal("Transferencia posible desde " + skill.from()));
        }
    }

    private static void collectEquivalenceSignals(
            Map<String, GapAggregate> aggregates,
            List<SkillEquivalenceSignal> signals
    ) {
        for (SkillEquivalenceSignal signal : safeList(signals)) {
            if (signal == null) {
                continue;
            }
            findTemplate(signal.targetSkill()).ifPresent(template -> aggregates
                    .computeIfAbsent(template.skillOrFamily(), ignored -> new GapAggregate(template))
                    .addRelatedSignal("Relacion parcial con " + signal.candidateSkill()));
        }
    }

    private static Optional<RoadmapTemplate> findTemplate(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return TEMPLATES.stream()
                .filter(template -> template.matches(normalized))
                .findFirst();
    }

    private static ProfileRoadmapSuggestion toSuggestion(
            GapAggregate aggregate,
            ProfileContext context,
            EvidenceCorpus corpus
    ) {
        RoadmapTemplate template = aggregate.template;
        boolean alreadyVisible = corpus.containsAny(template.aliases());
        boolean useAdvancedRoadmap = alreadyVisible || context.senior();
        String targetTone = targetTone(context);
        String toneLabel = alreadyVisible
                ? (context.senior() ? "Evidencia profesional" : "Refuerzo de evidencia")
                : targetTone;
        String title = alreadyVisible
                ? "Evidenciar mejor " + template.skillOrFamily()
                : template.title();
        String why = alreadyVisible
                ? "Aparece de forma repetida en ofertas cercanas y ya hay senales en el perfil. El foco podria ser mostrar evidencia mas concreta."
                : template.whyItMatters();
        List<String> steps = useAdvancedRoadmap
                ? advancedSteps(template, alreadyVisible)
                : template.initialSteps();

        return new ProfileRoadmapSuggestion(
                template.skillOrFamily(),
                title,
                why,
                steps,
                template.evidenceIdeas(),
                List.copyOf(aggregate.relatedSignals),
                toneLabel
        );
    }

    private static List<String> evidenceSteps(RoadmapTemplate template) {
        return List.of(
                "Revisar donde aparece " + template.skillOrFamily() + " en el CV y si tiene contexto suficiente.",
                "Agregar una explicacion breve de uso real, si corresponde.",
                "Vincularlo con un proyecto visible o ejemplo concreto si ya existe."
        );
    }

    private static List<String> advancedSteps(RoadmapTemplate template, boolean alreadyVisible) {
        return switch (template.skillOrFamily()) {
            case "SQL/PostgreSQL" -> List.of(
                    "Revisar modelado, consultas relevantes y decisiones de persistencia.",
                    "Si corresponde, evidenciar indices, transacciones o criterios de performance basica.",
                    "Mostrar como PostgreSQL se integra con una API, proceso o decision tecnica real."
            );
            case "REST APIs" -> List.of(
                    "Explicar criterios de diseno de recursos, contratos HTTP y versionado si corresponde.",
                    "Evidenciar validaciones, manejo de errores, paginacion, documentacion y testing.",
                    "Agregar contexto de observabilidad si ya existe: logs, trazas o metricas simples."
            );
            case "Cloud" -> List.of(
                    "Documentar decisiones de despliegue, configuracion y arquitectura.",
                    "Si corresponde, evidenciar manejo de variables, secretos, logs y monitoreo.",
                    "Mostrar como la app se conecta con servicios externos o base de datos."
            );
            default -> alreadyVisible
                    ? evidenceSteps(template)
                    : List.of(
                            "Profundizar el contexto de uso de " + template.skillOrFamily() + " en proyectos reales.",
                            "Documentar decisiones, problemas resueltos y validaciones aplicadas.",
                            "Conectar la brecha con evidencia concreta del perfil si corresponde."
                    );
        };
    }

    private static String targetTone(ProfileContext context) {
        if (context.senior()) {
            return "Profundizacion";
        }
        if (!context.targetRole().isBlank() && !"UNDECIDED".equals(context.targetRole())) {
            return "Orientado a " + context.targetRole();
        }
        if ("EXPLORATORY".equals(context.searchMode())) {
            return "Exploratorio";
        }
        return "Aprendizaje inicial";
    }

    private static int alignmentPriority(GapAggregate aggregate, ProfileContext context) {
        String family = aggregate.skillOrFamily();
        if ("Soporte/App Support".equals(family) && !supportAligned(context)) {
            return 2;
        }
        if (context.backendOrCloudTarget() && isBackendCloudFamily(family)) {
            return 0;
        }
        if ("UNDECIDED".equals(context.targetRole()) && isTechnicalCoreFamily(family)) {
            return 0;
        }
        return 1;
    }

    private static boolean supportAligned(ProfileContext context) {
        return context.supportTarget() || "EXPLORATORY".equals(context.searchMode());
    }

    private static boolean isBackendCloudFamily(String family) {
        return switch (family) {
            case "SQL/PostgreSQL", "Docker", "Kubernetes", "Cloud", "Kafka", "Spring Boot", "REST APIs", "Testing" -> true;
            default -> false;
        };
    }

    private static boolean isTechnicalCoreFamily(String family) {
        return isBackendCloudFamily(family) || "Frontend basico".equals(family);
    }

    private static String cleanCode(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ProfileContext(
            String targetRole,
            String targetSeniority,
            String searchMode,
            boolean senior
    ) {
        static ProfileContext from(CandidateProfile profile, EvidenceCorpus corpus) {
            String targetRole = cleanCode(profile.getTargetRole());
            String targetSeniority = cleanCode(profile.getTargetSeniority());
            String searchMode = cleanCode(profile.getSearchMode());
            boolean senior = "SENIOR".equals(targetSeniority)
                    || "LEAD".equals(targetSeniority)
                    || corpus.containsAny(List.of("senior", "lead", "principal", "staff", "arquitecto", "arquitectura", "architect"));
            return new ProfileContext(targetRole, targetSeniority, searchMode, senior);
        }

        boolean backendOrCloudTarget() {
            return switch (targetRole) {
                case "BACKEND", "DOTNET_BACKEND", "FULL_STACK", "DOTNET_FULLSTACK", "CLOUD", "DEVOPS" -> true;
                default -> false;
            };
        }

        boolean supportTarget() {
            return switch (targetRole) {
                case "IT_SUPPORT", "APP_SUPPORT", "SECURITY_OPS", "IAM" -> true;
                default -> false;
            };
        }
    }

    private record RoadmapTemplate(
            String skillOrFamily,
            List<String> aliases,
            String title,
            String whyItMatters,
            List<String> initialSteps,
            List<String> evidenceIdeas,
            String toneBase
    ) {
        boolean matches(String normalizedText) {
            return aliases.stream()
                    .map(ProfileRoadmapSuggestionService::normalize)
                    .anyMatch(alias -> containsAlias(normalizedText, alias));
        }
    }

    private static boolean containsAlias(String text, String alias) {
        if (text.isBlank() || alias.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(alias) + "($|[^a-z0-9])");
        return pattern.matcher(text).find();
    }

    private static class GapAggregate {
        private final RoadmapTemplate template;
        private final Set<String> relatedSignals = new LinkedHashSet<>();
        private int criticalResults;
        private int secondaryResults;

        private GapAggregate(RoadmapTemplate template) {
            this.template = template;
        }

        private void addGap(boolean critical) {
            if (critical) {
                criticalResults++;
            } else {
                secondaryResults++;
            }
        }

        private void addRelatedSignal(String signal) {
            if (signal != null && !signal.isBlank()) {
                relatedSignals.add(signal);
            }
        }

        private boolean hasRepeatedGap() {
            return criticalResults + secondaryResults >= 2;
        }

        private int criticalResults() {
            return criticalResults;
        }

        private int secondaryResults() {
            return secondaryResults;
        }

        private String skillOrFamily() {
            return template.skillOrFamily();
        }
    }

    private record EvidenceCorpus(String text) {
        static EvidenceCorpus from(CandidateProfile profile, List<CandidateProfileProject> projects) {
            StringBuilder out = new StringBuilder();
            append(out, profile.getCvText());
            append(out, profile.getDeclaredSkillsText());
            append(out, profile.getHeadline());
            append(out, profile.getSummary());
            for (CandidateProfileProject project : safeList(projects)) {
                if (project == null) {
                    continue;
                }
                append(out, project.getTitle());
                append(out, project.getDescription());
                append(out, project.getSkillsText());
            }
            return new EvidenceCorpus(normalize(out.toString()));
        }

        boolean containsAny(List<String> aliases) {
            return safeList(aliases).stream()
                    .map(ProfileRoadmapSuggestionService::normalize)
                    .anyMatch(alias -> containsAlias(text, alias));
        }

        private static void append(StringBuilder out, String value) {
            if (value != null && !value.isBlank()) {
                out.append(' ').append(value);
            }
        }
    }

    public record ProfileRoadmapSuggestion(
            String skillOrFamily,
            String title,
            String whyItMatters,
            List<String> initialSteps,
            List<String> evidenceIdeas,
            List<String> relatedSignals,
            String toneLabel
    ) {
    }
}
