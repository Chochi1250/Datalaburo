package com.DataLaburo.web.analysis.evidence;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.ProjectEvidenceType;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfessionalEvidenceService {
    private static final List<String> WORK_CONTEXT = List.of(
            "professional experience",
            "professional",
            "experiencia profesional",
            "experiencia laboral",
            "work experience",
            "experience",
            "years of experience",
            "year of experience",
            "years",
            "worked",
            "trabaje",
            "trabajo",
            "implemented",
            "maintained",
            "mantuve",
            "mantenimiento",
            "production",
            "supported production",
            "led",
            "migrated",
            "incidents",
            "incidentes",
            "tickets",
            "on call",
            "on-call",
            "operated",
            "operacion",
            "operado",
            "designed",
            "deployed",
            "managed",
            "soporte",
            "soporte tecnico",
            "troubleshooting",
            "diagnostico",
            "servidores",
            "company",
            "client",
            "team"
    );

    private static final List<String> STRONG_WORK_CONTEXT = List.of(
            "senior",
            "lead",
            "principal",
            "production",
            "supported production",
            "led",
            "migrated",
            "on call",
            "on-call"
    );

    private static final List<String> ACADEMIC_CONTEXT = List.of(
            "university",
            "universidad",
            "career",
            "carrera",
            "student",
            "estudiante",
            "coursework",
            "academic",
            "bootcamp",
            "course",
            "curso",
            "trabajo practico",
            "tp"
    );

    private static final List<String> TRANSITION_CONTEXT = List.of(
            "transitioning to",
            "transition to",
            "migrating to",
            "moving to",
            "learning",
            "studying",
            "targeting",
            "interested in",
            "training with",
            "basics",
            "self learning",
            "preparing for"
    );

    private static final List<String> PROJECT_CONTEXT = List.of(
            "project",
            "portfolio",
            "repository",
            "repo",
            "demo",
            "personal",
            "academic"
    );

    private static final List<String> DECLARED_LIST_CONTEXT = List.of(
            "skills",
            "technical skills",
            "technologies",
            "tools",
            "stack",
            "habilidades",
            "tecnologias",
            "herramientas"
    );

    private static final Pattern YEARS_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*(?:\\+\\s*)?(?:years|year|anos|anio|anios)\\b");

    private static final List<SkillRule> SKILL_RULES = List.of(
            rule("Spring Boot", ProfessionalDomain.BACKEND_JAVA, "spring boot", "springboot", "spring framework", "java spring", "spring"),
            rule("Java", ProfessionalDomain.BACKEND_JAVA, "java"),
            rule("REST APIs", ProfessionalDomain.BACKEND_JAVA, "rest api", "rest apis", "api rest", "apis rest"),
            rule("Backend", ProfessionalDomain.BACKEND_JAVA, "backend", "backend development", "backend developer", "microservices", "microservicios"),
            rule("C#", ProfessionalDomain.BACKEND_DOTNET, "c#", "c sharp"),
            rule(".NET", ProfessionalDomain.BACKEND_DOTNET, ".net", "dotnet", "asp.net", "asp net"),
            rule("App Support", ProfessionalDomain.APP_SUPPORT, "app support", "application support", "production support"),
            rule("IT Support", ProfessionalDomain.SUPPORT, "technical support", "it support", "help desk", "service desk", "support analyst", "support engineer", "soporte tecnico"),
            rule("Tickets", ProfessionalDomain.SUPPORT, "tickets", "service tickets", "incident tickets"),
            rule("Troubleshooting", ProfessionalDomain.SUPPORT, "troubleshooting", "diagnostico", "diagnosis"),
            rule("Windows Server", ProfessionalDomain.INFRA, "windows server"),
            rule("Linux", ProfessionalDomain.INFRA, "linux"),
            rule("Active Directory", ProfessionalDomain.INFRA, "active directory"),
            rule("Networking", ProfessionalDomain.INFRA, "networking", "networks", "redes"),
            rule("Infrastructure", ProfessionalDomain.INFRA, "infrastructure", "infraestructura", "infra"),
            rule("OpenShift", ProfessionalDomain.INFRA, "openshift", "red hat openshift"),
            rule("Storage", ProfessionalDomain.INFRA, "storage", "enterprise storage", "almacenamiento", "ds8000", "flashsystem"),
            rule("Servers", ProfessionalDomain.INFRA, "servers", "servidores"),
            rule("ITIL", ProfessionalDomain.SUPPORT, "itil", "itsm"),
            rule("Git", ProfessionalDomain.BACKEND_JAVA, "git"),
            rule("Docker", ProfessionalDomain.CLOUD, "docker", "docker compose"),
            rule("Kubernetes", ProfessionalDomain.CLOUD, "kubernetes", "k8s"),
            rule("Cloud", ProfessionalDomain.CLOUD, "cloud", "aws", "azure", "gcp"),
            rule("PostgreSQL", ProfessionalDomain.DATA, "postgresql", "postgres", "postgre sql"),
            rule("SQL", ProfessionalDomain.DATA, "sql"),
            rule("Power BI", ProfessionalDomain.DATA, "power bi", "powerbi"),
            rule("Excel", ProfessionalDomain.DATA, "excel"),
            rule("React", ProfessionalDomain.FRONTEND, "react", "react.js", "reactjs"),
            rule("Angular", ProfessionalDomain.FRONTEND, "angular"),
            rule("Frontend", ProfessionalDomain.FRONTEND, "frontend", "front end", "javascript", "typescript", "html", "css"),
            rule("QA", ProfessionalDomain.QA, "qa", "testing", "test automation", "junit", "mockito"),
            rule("Security", ProfessionalDomain.SECURITY, "security", "cybersecurity", "ciberseguridad", "oauth", "saml", "iam")
    );

    public ProfileEvidenceSummary summarizeProfile(CandidateProfile profile, List<CandidateProfileProject> projects) {
        Objects.requireNonNull(profile, "profile must not be null");

        Map<String, ProfessionalSkillEvidence> evidenceBySkill = new LinkedHashMap<>();
        String cvText = safe(profile.getCvText());
        String normalizedCv = normalizeText(cvText);

        addWorkEvidence(evidenceBySkill, normalizedCv, cvText);
        addProjectEvidence(evidenceBySkill, projects == null ? List.of() : projects);
        addAcademicEvidence(evidenceBySkill, normalizedCv, cvText);

        List<SkillSignal> declaredSkills = declaredSkillSignals(profile);
        addTransferableEvidence(evidenceBySkill, declaredSkills, normalizedCv);
        addDeclaredOnlyEvidence(evidenceBySkill, declaredSkills);

        List<ProfessionalSkillEvidence> skillEvidence = evidenceBySkill.values().stream()
                .sorted(Comparator
                        .comparingInt(ProfessionalEvidenceService::evidencePriority)
                        .reversed()
                        .thenComparing(ProfessionalSkillEvidence::skillName))
                .toList();
        List<SeniorityByDomain> seniorityByDomain = seniorityByDomain(skillEvidence, normalizedCv);
        List<ProfessionalDomain> strongDomains = strongDomains(skillEvidence);
        List<ProfessionalDomain> transitionDomains = transitionDomains(skillEvidence, strongDomains);
        List<String> declaredOnlySkills = skillEvidence.stream()
                .filter(evidence -> evidence.evidenceType() == ProfessionalEvidenceType.DECLARED_ONLY)
                .map(ProfessionalSkillEvidence::skillName)
                .toList();

        return new ProfileEvidenceSummary(
                profile.getId(),
                skillEvidence,
                seniorityByDomain,
                strongDomains,
                transitionDomains,
                declaredOnlySkills,
                List.of()
        );
    }

    private void addWorkEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, String normalizedCv, String cvText) {
        if (normalizedCv.isBlank()) {
            return;
        }
        for (SkillRule rule : SKILL_RULES) {
            if (!containsRule(normalizedCv, rule)) {
                continue;
            }
            if (!hasWorkEvidenceForRule(normalizedCv, rule)) {
                continue;
            }
            addEvidence(evidenceBySkill, new ProfessionalSkillEvidence(
                    rule.label(),
                    ProfessionalEvidenceType.WORK_EXPERIENCE,
                    workStrength(normalizedCv),
                    resolvedDomain(rule, normalizedCv),
                    ProfessionalEvidenceSource.CV_TEXT,
                    "cvText",
                    contextFor(cvText),
                    List.of()
            ));
        }
    }

    private void addProjectEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, List<CandidateProfileProject> projects) {
        for (CandidateProfileProject project : projects) {
            if (project == null) {
                continue;
            }
            String combined = String.join(" ",
                    safe(project.getTitle()),
                    safe(project.getDescription()),
                    safe(project.getSkillsText())
            );
            String normalized = normalizeText(combined);
            if (normalized.isBlank()) {
                continue;
            }
            ProfessionalEvidenceStrength strength = projectStrength(project);
            for (SkillRule rule : SKILL_RULES) {
                if (!containsRule(normalized, rule)) {
                    continue;
                }
                addEvidence(evidenceBySkill, new ProfessionalSkillEvidence(
                        rule.label(),
                        ProfessionalEvidenceType.PROJECT,
                        strength,
                        resolvedDomain(rule, normalized),
                        ProfessionalEvidenceSource.PROJECT,
                        safe(project.getTitle()),
                        contextFor(combined),
                        List.of("Project evidence does not equal senior work experience.")
                ));
            }
        }
    }

    private void addAcademicEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, String normalizedCv, String cvText) {
        if (normalizedCv.isBlank() || !containsAny(normalizedCv, ACADEMIC_CONTEXT)) {
            return;
        }
        for (SkillRule rule : SKILL_RULES) {
            if (!containsRule(normalizedCv, rule)) {
                continue;
            }
            addEvidence(evidenceBySkill, new ProfessionalSkillEvidence(
                    rule.label(),
                    ProfessionalEvidenceType.ACADEMIC,
                    academicStrength(normalizedCv),
                    resolvedDomain(rule, normalizedCv),
                    ProfessionalEvidenceSource.CV_TEXT,
                    "cvText",
                    contextFor(cvText),
                    List.of()
            ));
        }
    }

    private void addTransferableEvidence(
            Map<String, ProfessionalSkillEvidence> evidenceBySkill,
            List<SkillSignal> declaredSkills,
            String normalizedCv
    ) {
        boolean dotnetWork = hasEvidence(evidenceBySkill, "C#", ProfessionalEvidenceType.WORK_EXPERIENCE)
                || hasEvidence(evidenceBySkill, ".NET", ProfessionalEvidenceType.WORK_EXPERIENCE);
        if (dotnetWork) {
            maybeAddDotnetToJavaTransfer(evidenceBySkill, declaredSkills, normalizedCv, "Java");
            maybeAddDotnetToJavaTransfer(evidenceBySkill, declaredSkills, normalizedCv, "Spring Boot");
        }
    }

    private void maybeAddDotnetToJavaTransfer(
            Map<String, ProfessionalSkillEvidence> evidenceBySkill,
            List<SkillSignal> declaredSkills,
            String normalizedCv,
            String targetSkill
    ) {
        if (hasDirectEvidence(evidenceBySkill, targetSkill)) {
            return;
        }
        Optional<SkillRule> targetRule = ruleByLabel(targetSkill);
        if (targetRule.isEmpty()) {
            return;
        }
        boolean mentionedOrDeclared = declaredSkills.stream()
                .anyMatch(signal -> signal.label().equals(targetSkill))
                || containsRule(normalizedCv, targetRule.get());
        if (!mentionedOrDeclared) {
            return;
        }
        addEvidence(evidenceBySkill, new ProfessionalSkillEvidence(
                targetSkill,
                ProfessionalEvidenceType.TRANSFERABLE,
                ProfessionalEvidenceStrength.WEAK,
                ProfessionalDomain.BACKEND_JAVA,
                ProfessionalEvidenceSource.TRANSFER_RULE,
                ".NET/C# -> Java backend",
                "Object-oriented backend experience may transfer, but it is not direct Java/Spring work evidence.",
                List.of("Transferable signal, not direct evidence.")
        ));
    }

    private void addDeclaredOnlyEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, List<SkillSignal> declaredSkills) {
        for (SkillSignal signal : declaredSkills) {
            if (signal.label().isBlank() || hasAnyEvidence(evidenceBySkill, signal.label())) {
                continue;
            }
            addEvidence(evidenceBySkill, new ProfessionalSkillEvidence(
                    signal.label(),
                    ProfessionalEvidenceType.DECLARED_ONLY,
                    ProfessionalEvidenceStrength.WEAK,
                    signal.domain(),
                    ProfessionalEvidenceSource.DECLARED_SKILLS,
                    "declaredSkillsText",
                    signal.raw(),
                    List.of("Declared skill without supporting evidence.")
            ));
        }
    }

    private List<SeniorityByDomain> seniorityByDomain(List<ProfessionalSkillEvidence> skillEvidence, String normalizedCv) {
        Map<ProfessionalDomain, List<ProfessionalSkillEvidence>> byDomain = new LinkedHashMap<>();
        for (ProfessionalSkillEvidence evidence : skillEvidence) {
            if (evidence.domain() == ProfessionalDomain.UNKNOWN) {
                continue;
            }
            byDomain.computeIfAbsent(evidence.domain(), ignored -> new ArrayList<>()).add(evidence);
        }

        List<SeniorityByDomain> out = new ArrayList<>();
        for (Map.Entry<ProfessionalDomain, List<ProfessionalSkillEvidence>> entry : byDomain.entrySet()) {
            ProfessionalDomain domain = entry.getKey();
            List<ProfessionalSkillEvidence> evidence = entry.getValue();
            Optional<ProfessionalSkillEvidence> workEvidence = evidence.stream()
                    .filter(ProfessionalSkillEvidence::isDirectWorkEvidence)
                    .findFirst();
            if (workEvidence.isPresent()) {
                out.add(workSeniority(domain, workEvidence.get(), normalizedCv));
                continue;
            }
            Optional<ProfessionalSkillEvidence> transitionEvidence = evidence.stream()
                    .filter(item -> item.evidenceType() == ProfessionalEvidenceType.PROJECT
                            || item.evidenceType() == ProfessionalEvidenceType.ACADEMIC
                            || item.evidenceType() == ProfessionalEvidenceType.TRANSFERABLE)
                    .findFirst();
            transitionEvidence.ifPresent(item -> out.add(transitionSeniority(domain, item, normalizedCv)));
        }
        return out;
    }

    private SeniorityByDomain workSeniority(
            ProfessionalDomain domain,
            ProfessionalSkillEvidence evidence,
            String normalizedCv
    ) {
        int years = maxYears(normalizedCv);
        if (containsAny(normalizedCv, List.of("senior", "lead", "principal")) || years >= 5) {
            return new SeniorityByDomain(
                    domain,
                    "SENIOR",
                    ProfessionalEvidenceType.WORK_EXPERIENCE,
                    ProfessionalEvidenceStrength.STRONG,
                    "Work evidence plus senior/5+ years signal in cvText."
            );
        }
        if (years >= 3) {
            return new SeniorityByDomain(
                    domain,
                    "MID",
                    ProfessionalEvidenceType.WORK_EXPERIENCE,
                    ProfessionalEvidenceStrength.MEDIUM,
                    "Work evidence plus 3+ years signal in cvText."
            );
        }
        if (years > 0 || containsAny(normalizedCv, List.of("junior", "jr"))) {
            return new SeniorityByDomain(
                    domain,
                    "JUNIOR",
                    ProfessionalEvidenceType.WORK_EXPERIENCE,
                    ProfessionalEvidenceStrength.MEDIUM,
                    "Work evidence with junior/early-career signal in cvText."
            );
        }
        return new SeniorityByDomain(
                domain,
                "WORK_EXPERIENCE",
                ProfessionalEvidenceType.WORK_EXPERIENCE,
                evidence.strength(),
                "Work evidence detected, but no explicit years or seniority signal."
        );
    }

    private SeniorityByDomain transitionSeniority(
            ProfessionalDomain domain,
            ProfessionalSkillEvidence evidence,
            String normalizedCv
    ) {
        String seniority = containsAny(normalizedCv, List.of("trainee", "student", "estudiante"))
                ? "TRAINEE"
                : "JUNIOR";
        return new SeniorityByDomain(
                domain,
                seniority,
                evidence.evidenceType(),
                ProfessionalEvidenceStrength.WEAK,
                "Evidence is not senior work experience for this domain."
        );
    }

    private List<ProfessionalDomain> strongDomains(List<ProfessionalSkillEvidence> skillEvidence) {
        Set<ProfessionalDomain> domains = new LinkedHashSet<>();
        for (ProfessionalSkillEvidence evidence : skillEvidence) {
            if (evidence.domain() == ProfessionalDomain.UNKNOWN) {
                continue;
            }
            if (evidence.evidenceType() == ProfessionalEvidenceType.WORK_EXPERIENCE
                    && evidence.strength().atLeast(ProfessionalEvidenceStrength.MEDIUM)) {
                domains.add(evidence.domain());
            }
        }
        return List.copyOf(domains);
    }

    private List<ProfessionalDomain> transitionDomains(
            List<ProfessionalSkillEvidence> skillEvidence,
            List<ProfessionalDomain> strongDomains
    ) {
        Set<ProfessionalDomain> strong = new LinkedHashSet<>(strongDomains);
        Set<ProfessionalDomain> domains = new LinkedHashSet<>();
        for (ProfessionalSkillEvidence evidence : skillEvidence) {
            if (evidence.domain() == ProfessionalDomain.UNKNOWN || strong.contains(evidence.domain())) {
                continue;
            }
            if (evidence.evidenceType() == ProfessionalEvidenceType.PROJECT
                    || evidence.evidenceType() == ProfessionalEvidenceType.ACADEMIC
                    || evidence.evidenceType() == ProfessionalEvidenceType.TRANSFERABLE) {
                domains.add(evidence.domain());
            }
        }
        return List.copyOf(domains);
    }

    private boolean hasWorkEvidenceForRule(String normalizedCv, SkillRule rule) {
        for (String alias : rule.normalizedAliases()) {
            int from = 0;
            while (from < normalizedCv.length()) {
                int index = indexOfAlias(normalizedCv, alias, from);
                if (index < 0) {
                    break;
                }
                if (hasLearningContextNearAlias(normalizedCv, index, alias.length())) {
                    from = index + alias.length();
                    continue;
                }
                String window = window(normalizedCv, index, alias.length(), 70);
                if (containsAny(window, ACADEMIC_CONTEXT)) {
                    from = index + alias.length();
                    continue;
                }
                if (isProjectOnlyContext(window)) {
                    from = index + alias.length();
                    continue;
                }
                if (isDeclaredListContext(window)) {
                    from = index + alias.length();
                    continue;
                }
                if (containsAny(window, WORK_CONTEXT)) {
                    return true;
                }
                from = index + alias.length();
            }
        }
        return false;
    }

    private boolean hasLearningContextNearAlias(String normalizedCv, int aliasIndex, int aliasLength) {
        int start = Math.max(0, aliasIndex - 60);
        String before = normalizedCv.substring(start, aliasIndex);
        if (containsAny(before, TRANSITION_CONTEXT)) {
            return true;
        }
        int afterStart = Math.min(normalizedCv.length(), aliasIndex + aliasLength);
        int afterEnd = Math.min(normalizedCv.length(), afterStart + 45);
        String after = normalizedCv.substring(afterStart, afterEnd);
        return containsAny(after, List.of("training", "basics", "course", "coursework", "bootcamp"));
    }

    private boolean isProjectOnlyContext(String window) {
        return containsAny(window, PROJECT_CONTEXT)
                && !containsAny(window, List.of("production", "professional", "worked", "company", "client", "team", "maintained", "on call", "on-call"));
    }

    private boolean isDeclaredListContext(String window) {
        return containsAny(window, DECLARED_LIST_CONTEXT);
    }

    private ProfessionalEvidenceStrength workStrength(String normalizedCv) {
        int years = maxYears(normalizedCv);
        if (years >= 5 || containsAny(normalizedCv, STRONG_WORK_CONTEXT)) {
            return ProfessionalEvidenceStrength.STRONG;
        }
        if (years > 0 || containsAny(normalizedCv, WORK_CONTEXT)) {
            return ProfessionalEvidenceStrength.MEDIUM;
        }
        return ProfessionalEvidenceStrength.WEAK;
    }

    private ProfessionalEvidenceStrength academicStrength(String normalizedCv) {
        if (containsAny(normalizedCv, List.of("university", "universidad", "career", "carrera", "bootcamp", "coursework"))) {
            return ProfessionalEvidenceStrength.MEDIUM;
        }
        return ProfessionalEvidenceStrength.WEAK;
    }

    private ProfessionalEvidenceStrength projectStrength(CandidateProfileProject project) {
        ProjectEvidenceType type = project.getEvidenceType() == null ? ProjectEvidenceType.OTHER : project.getEvidenceType();
        boolean hasUrl = !safe(project.getRepositoryUrl()).isBlank() || !safe(project.getDemoUrl()).isBlank();
        boolean hasTechnicalDescription = safe(project.getDescription()).length() >= 80;
        return switch (type) {
            case WORK_PROJECT -> ProfessionalEvidenceStrength.STRONG;
            case PERSONAL_PROJECT, ACADEMIC_PROJECT -> hasUrl || hasTechnicalDescription
                    ? ProfessionalEvidenceStrength.MEDIUM
                    : ProfessionalEvidenceStrength.WEAK;
            case COURSE_PROJECT, OTHER -> hasUrl && hasTechnicalDescription
                    ? ProfessionalEvidenceStrength.MEDIUM
                    : ProfessionalEvidenceStrength.WEAK;
        };
    }

    private List<SkillSignal> declaredSkillSignals(CandidateProfile profile) {
        return profile.getDeclaredSkillTags().stream()
                .map(this::skillSignalFor)
                .filter(signal -> !signal.label().isBlank())
                .distinct()
                .toList();
    }

    private SkillSignal skillSignalFor(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return new SkillSignal("", ProfessionalDomain.UNKNOWN, "");
        }
        for (SkillRule rule : SKILL_RULES) {
            if (containsRule(normalized, rule)) {
                return new SkillSignal(rule.label(), resolvedDomain(rule, normalized), raw.trim());
            }
        }
        return new SkillSignal(raw.trim(), ProfessionalDomain.UNKNOWN, raw.trim());
    }

    private static ProfessionalDomain resolvedDomain(SkillRule rule, String normalizedContext) {
        if (rule.label().equals("Backend") || rule.label().equals("REST APIs")) {
            if (containsRule(normalizedContext, requiredRule(".NET"))
                    || containsRule(normalizedContext, requiredRule("C#"))) {
                return ProfessionalDomain.BACKEND_DOTNET;
            }
            if (containsRule(normalizedContext, requiredRule("Java"))
                    || containsRule(normalizedContext, requiredRule("Spring Boot"))) {
                return ProfessionalDomain.BACKEND_JAVA;
            }
        }
        return rule.domain();
    }

    private static SkillRule requiredRule(String label) {
        return SKILL_RULES.stream()
                .filter(rule -> rule.label().equals(label))
                .findFirst()
                .orElseThrow();
    }

    private Optional<SkillRule> ruleByLabel(String label) {
        return SKILL_RULES.stream()
                .filter(rule -> rule.label().equals(label))
                .findFirst();
    }

    private void addEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, ProfessionalSkillEvidence candidate) {
        String key = evidenceKey(candidate.skillName());
        ProfessionalSkillEvidence current = evidenceBySkill.get(key);
        if (current == null || evidencePriority(candidate) > evidencePriority(current)) {
            evidenceBySkill.put(key, candidate);
        }
    }

    private static int evidencePriority(ProfessionalSkillEvidence evidence) {
        return evidenceTypePriority(evidence.evidenceType()) + strengthScore(evidence.strength());
    }

    private static int evidenceTypePriority(ProfessionalEvidenceType type) {
        return switch (type) {
            case WORK_EXPERIENCE -> 50;
            case PROJECT -> 40;
            case ACADEMIC -> 30;
            case TRANSFERABLE -> 20;
            case DECLARED_ONLY -> 10;
            case MISSING -> 0;
        };
    }

    private static int strengthScore(ProfessionalEvidenceStrength strength) {
        return switch (strength) {
            case STRONG -> 3;
            case MEDIUM -> 2;
            case WEAK -> 1;
            case NONE -> 0;
        };
    }

    private boolean hasEvidence(
            Map<String, ProfessionalSkillEvidence> evidenceBySkill,
            String skill,
            ProfessionalEvidenceType type
    ) {
        ProfessionalSkillEvidence evidence = evidenceBySkill.get(evidenceKey(skill));
        return evidence != null && evidence.evidenceType() == type;
    }

    private boolean hasDirectEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, String skill) {
        ProfessionalSkillEvidence evidence = evidenceBySkill.get(evidenceKey(skill));
        if (evidence == null) {
            return false;
        }
        return evidence.evidenceType() == ProfessionalEvidenceType.WORK_EXPERIENCE
                || evidence.evidenceType() == ProfessionalEvidenceType.PROJECT
                || evidence.evidenceType() == ProfessionalEvidenceType.ACADEMIC;
    }

    private boolean hasAnyEvidence(Map<String, ProfessionalSkillEvidence> evidenceBySkill, String skill) {
        return evidenceBySkill.containsKey(evidenceKey(skill));
    }

    private static boolean containsRule(String normalizedText, SkillRule rule) {
        return rule.normalizedAliases().stream()
                .anyMatch(alias -> indexOfAlias(normalizedText, alias, 0) >= 0);
    }

    private static int indexOfAlias(String normalizedText, String normalizedAlias, int fromIndex) {
        int from = Math.max(0, fromIndex);
        while (from < normalizedText.length()) {
            int index = normalizedText.indexOf(normalizedAlias, from);
            if (index < 0) {
                return -1;
            }
            int end = index + normalizedAlias.length();
            if (isBoundary(normalizedText, index - 1) && isBoundary(normalizedText, end)) {
                return index;
            }
            from = index + 1;
        }
        return -1;
    }

    private static boolean isBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char ch = text.charAt(index);
        return !Character.isLetterOrDigit(ch) && ch != '#' && ch != '+' && ch != '.';
    }

    private static boolean containsAny(String text, List<String> candidates) {
        for (String candidate : candidates) {
            if (indexOfAlias(text, normalizeText(candidate), 0) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String window(String text, int index, int length, int radius) {
        int start = Math.max(0, index - radius);
        int end = Math.min(text.length(), index + length + radius);
        return text.substring(start, end);
    }

    private static int maxYears(String normalizedCv) {
        Matcher matcher = YEARS_PATTERN.matcher(normalizedCv);
        int max = 0;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static String contextFor(String text) {
        String cleaned = safe(text).replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 180) {
            return cleaned;
        }
        return cleaned.substring(0, 177) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String evidenceKey(String value) {
        return normalizeText(value);
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9#.+]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static SkillRule rule(String label, ProfessionalDomain domain, String... aliases) {
        return new SkillRule(label, domain, List.of(aliases));
    }

    private record SkillRule(
            String label,
            ProfessionalDomain domain,
            List<String> aliases
    ) {
        private SkillRule {
            aliases = aliases.stream()
                    .map(ProfessionalEvidenceService::normalizeText)
                    .filter(alias -> !alias.isBlank())
                    .toList();
        }

        private List<String> normalizedAliases() {
            return aliases;
        }
    }

    private record SkillSignal(
            String label,
            ProfessionalDomain domain,
            String raw
    ) {
    }
}
