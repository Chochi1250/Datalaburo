package com.DataLaburo.web.controller;

import com.DataLaburo.web.analysis.CompatibilityAnalysisException;
import com.DataLaburo.web.analysis.RerankSignal;
import com.DataLaburo.web.analysis.TransferableSkill;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityResponse;
import com.DataLaburo.web.analysis.VectorFirstCompatibilityService;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.service.CandidateProfileService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class ProfileVectorCompatibilityController {
    private static final int DEFAULT_LIMIT = 20;

    private final CandidateProfileService candidateProfileService;
    private final ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider;

    public ProfileVectorCompatibilityController(
            CandidateProfileService candidateProfileService,
            ObjectProvider<VectorFirstCompatibilityService> compatibilityServiceProvider
    ) {
        this.candidateProfileService = candidateProfileService;
        this.compatibilityServiceProvider = compatibilityServiceProvider;
    }

    @GetMapping("/profiles/{profileId}/vector-first-compatibility")
    public String vectorFirstCompatibility(
            @PathVariable Long profileId,
            @RequestParam(value = "limit", required = false) String rawLimit,
            Model model
    ) {
        Optional<CandidateProfile> profile = candidateProfileService.findById(profileId);
        if (profile.isEmpty()) {
            model.addAttribute("profileId", profileId);
            model.addAttribute("error", "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.");
            model.addAttribute("limit", DEFAULT_LIMIT);
            return "profile-vector-compatibility";
        }

        int limit = parseLimit(rawLimit, model);
        model.addAttribute("profile", profile.get());
        model.addAttribute("limit", limit);

        VectorFirstCompatibilityService compatibilityService = compatibilityServiceProvider.getIfAvailable();
        if (compatibilityService == null) {
            model.addAttribute("error", "La vista vector-first requiere PostgreSQL + pgvector y embeddings BAAI/bge-m3. H2 queda como legado historico/test.");
            return "profile-vector-compatibility";
        }

        try {
            VectorFirstCompatibilityResponse response = compatibilityService.analyze(profileId, limit);
            model.addAttribute("response", response);
            model.addAttribute("results", response.results() == null
                    ? List.of()
                    : response.results().stream().map(result -> ResultView.from(result, profile.get())).toList());
            model.addAttribute("limit", response.retrieval() == null ? limit : response.retrieval().limit());
        } catch (CompatibilityAnalysisException e) {
            model.addAttribute("error", friendlyMessage(e.getMessage()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            model.addAttribute("error", "No se pudo ejecutar la compatibilidad vector-first. Revisa que PostgreSQL, pgvector y los embeddings READY esten disponibles.");
        }

        return "profile-vector-compatibility";
    }

    private static int parseLimit(String rawLimit, Model model) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(rawLimit.trim());
            if (limit <= 0) {
                model.addAttribute("warning", "El limite indicado no es valido. Se usa 20 por defecto.");
                return DEFAULT_LIMIT;
            }
            return limit;
        } catch (NumberFormatException e) {
            model.addAttribute("warning", "El limite indicado no es numerico. Se usa 20 por defecto.");
            return DEFAULT_LIMIT;
        }
    }

    private static String friendlyMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No se pudo ejecutar la compatibilidad vector-first.";
        }
        if (message.contains("No READY PROFILE embedding")) {
            return "Este perfil todavia no tiene embedding listo para BAAI/bge-m3. Procesa embeddings pendientes desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY PROFILE embedding") && message.contains("unsupported vector dimensions")) {
            return "El embedding del perfil no tiene las 1024 dimensiones esperadas para BAAI/bge-m3. Reprocesa el embedding antes de ejecutar esta vista.";
        }
        if (message.contains("No READY JOB embeddings") || message.contains("no READY JOB embeddings")) {
            return "No hay ofertas con embeddings READY para BAAI/bge-m3. Procesa embeddings de JOBS desde los endpoints internos antes de ejecutar esta vista.";
        }
        if (message.contains("READY JOB embeddings") && message.contains("unsupported vector dimensions")) {
            return "Las ofertas tienen embeddings con dimensiones incompatibles. Se esperan 1024 dimensiones para BAAI/bge-m3.";
        }
        if (message.contains("Vector search returned no READY JOB embeddings")) {
            return "La busqueda vectorial no encontro ofertas con embeddings disponibles.";
        }
        if (message.contains("Candidate profile not found")) {
            return "No se encontro el perfil seleccionado. Volve a perfiles y elegi otro.";
        }
        return message;
    }

    private record ResultView(
            Long jobId,
            String title,
            String company,
            int vectorRank,
            double vectorSimilarity,
            int analysisRank,
            String detectedRoleLabel,
            String detectedRoleCode,
            String detectedSeniorityLabel,
            String detectedSeniorityCode,
            String categoryLabel,
            String categoryCode,
            String evidenceLabel,
            String evidenceCode,
            String confidenceLabel,
            String confidenceCode,
            List<String> matchedSkills,
            List<String> missingCriticalSkills,
            List<String> missingSecondarySkills,
            List<TransferView> transferableSkills,
            List<String> roadmapSuggestions,
            String explanation,
            String bucketLabel,
            String bucketCode,
            Integer suggestedRerankRank,
            Integer suggestedRankDelta,
            List<String> rerankReasons,
            List<String> rerankWarnings,
            List<SignalView> rerankSignals,
            List<TargetDiagnosticView> targetDiagnostics,
            boolean hasDiagnostic
    ) {
        static ResultView from(VectorFirstCompatibilityResult result, CandidateProfile profile) {
            String bucketCode = result.compatibilityBucket() == null ? null : result.compatibilityBucket().name();
            Integer suggestedRank = result.suggestedRerankRank();
            Integer suggestedDelta = result.suggestedRankDelta();
            List<String> warnings = safeList(result.rerankWarnings());
            List<String> reasons = safeList(result.rerankReasons());
            List<SignalView> signals = safeList(result.rerankSignals()).stream()
                    .map(SignalView::from)
                    .toList();
            boolean hasDiagnostic = bucketCode != null
                    || suggestedRank != null
                    || suggestedDelta != null
                    || !warnings.isEmpty()
                    || !reasons.isEmpty()
                    || !signals.isEmpty();

            return new ResultView(
                    result.jobId(),
                    result.title(),
                    result.company(),
                    result.vectorRank(),
                    result.vectorSimilarity(),
                    result.analysisRank(),
                    labelRole(result.detectedRole()),
                    codeOrUnknown(result.detectedRole()),
                    labelSeniority(result.detectedSeniority()),
                    codeOrUnknown(result.detectedSeniority()),
                    labelCategory(result.compatibilityCategory() == null ? null : result.compatibilityCategory().name()),
                    result.compatibilityCategory() == null ? null : result.compatibilityCategory().name(),
                    labelEvidence(result.evidenceLevel() == null ? null : result.evidenceLevel().name()),
                    result.evidenceLevel() == null ? null : result.evidenceLevel().name(),
                    labelConfidence(result.confidence() == null ? null : result.confidence().name()),
                    result.confidence() == null ? null : result.confidence().name(),
                    safeList(result.matchedSkills()),
                    safeList(result.missingCriticalSkills()),
                    safeList(result.missingSecondarySkills()),
                    safeList(result.transferableSkills()).stream().map(TransferView::from).toList(),
                    safeList(result.roadmapSuggestions()),
                    result.explanation(),
                    labelBucket(bucketCode),
                    bucketCode,
                    suggestedRank,
                    suggestedDelta,
                    reasons,
                    warnings,
                    signals,
                    buildTargetDiagnostics(profile, result),
                    hasDiagnostic
            );
        }
    }

    private record TransferView(
            String from,
            String to,
            String strengthLabel,
            String strengthCode,
            String reason
    ) {
        static TransferView from(TransferableSkill skill) {
            String strengthCode = skill.strength() == null ? null : skill.strength().name();
            return new TransferView(
                    skill.from(),
                    skill.to(),
                    labelTransferStrength(strengthCode),
                    strengthCode,
                    skill.reason()
            );
        }
    }

    private record SignalView(
            String name,
            String polarityLabel,
            String polarityCode,
            String detail
    ) {
        static SignalView from(RerankSignal signal) {
            String polarityCode = signal.polarity() == null ? null : signal.polarity().name();
            return new SignalView(
                    signal.name(),
                    labelPolarity(polarityCode),
                    polarityCode,
                    signal.detail()
            );
        }
    }

    private record TargetDiagnosticView(
            String label,
            String detail,
            String category
    ) {
    }

    private static List<TargetDiagnosticView> buildTargetDiagnostics(CandidateProfile profile, VectorFirstCompatibilityResult result) {
        if (profile == null || result == null) {
            return List.of();
        }

        String targetRole = codeOrDefault(profile.getTargetRole(), "UNDECIDED");
        String targetSeniority = codeOrDefault(profile.getTargetSeniority(), "ANY");
        String searchMode = codeOrDefault(profile.getSearchMode(), "FOCUSED");
        String detectedRole = codeOrUnknown(result.detectedRole());
        String detectedSeniority = codeOrUnknown(result.detectedSeniority());

        List<TargetDiagnosticView> out = new java.util.ArrayList<>();
        out.add(roleDiagnostic(targetRole, detectedRole, searchMode));
        out.add(seniorityDiagnostic(targetSeniority, detectedSeniority));
        if (isAdjacentRole(targetRole, detectedRole) && !"FOCUSED".equals(searchMode)) {
            out.add(new TargetDiagnosticView(
                    "Oportunidad adyacente",
                    "El modo de busqueda permite revisar roles cercanos al objetivo.",
                    "adjacent"
            ));
        }
        out.add(new TargetDiagnosticView(
                "Modo: " + labelSearchMode(searchMode),
                modeDetail(searchMode),
                "mode"
        ));
        return out;
    }

    private static TargetDiagnosticView roleDiagnostic(String targetRole, String detectedRole, String searchMode) {
        if ("UNDECIDED".equals(targetRole)) {
            return new TargetDiagnosticView(
                    "Rol objetivo sin definir",
                    "El perfil no prioriza un rol especifico.",
                    "neutral"
            );
        }
        if (rolesAligned(targetRole, detectedRole)) {
            return new TargetDiagnosticView(
                    "Rol alineado",
                    "La oferta detectada coincide con el rol objetivo.",
                    "positive"
            );
        }
        if (isAdjacentRole(targetRole, detectedRole)) {
            String detail = "FOCUSED".equals(searchMode)
                    ? "Es un rol cercano, pero no es el foco principal declarado."
                    : "Es un rol cercano al objetivo declarado.";
            return new TargetDiagnosticView("Oportunidad adyacente", detail, "adjacent");
        }
        return new TargetDiagnosticView(
                "Rol no prioritario",
                "La oferta detectada no coincide con el rol objetivo.",
                "warning"
        );
    }

    private static TargetDiagnosticView seniorityDiagnostic(String targetSeniority, String detectedSeniority) {
        if ("ANY".equals(targetSeniority)) {
            return new TargetDiagnosticView(
                    "Seniority abierto",
                    "El perfil acepta evaluar distintos niveles.",
                    "neutral"
            );
        }
        int targetRank = seniorityRank(targetSeniority);
        int detectedRank = seniorityRank(detectedSeniority);
        if (targetRank <= 0 || detectedRank <= 0) {
            return new TargetDiagnosticView(
                    "Seniority no concluyente",
                    "No hay senales suficientes para comparar el seniority.",
                    "neutral"
            );
        }
        if (targetRank == detectedRank || Math.abs(targetRank - detectedRank) == 1) {
            return new TargetDiagnosticView(
                    "Seniority compatible",
                    "El seniority detectado esta cerca del objetivo.",
                    "positive"
            );
        }
        if (targetRank > detectedRank) {
            return new TargetDiagnosticView(
                    "Posible sobrecalificacion",
                    "El objetivo declarado esta por encima del seniority detectado en la oferta.",
                    "warning"
            );
        }
        return new TargetDiagnosticView(
                "Seniority aspiracional",
                "La oferta parece pedir un nivel superior al objetivo declarado.",
                "warning"
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String codeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String codeOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String labelCategory(String code) {
        return switch (codeOrUnknown(code)) {
            case "STRONG_MATCH" -> "Match fuerte";
            case "GOOD_MATCH_WITH_MINOR_GAPS" -> "Buen match con brechas menores";
            case "TRANSFERABLE_OPPORTUNITY" -> "Oportunidad transferible";
            case "ASPIRATIONAL_MATCH" -> "Match aspiracional";
            case "KEYWORD_MATCH_RISK" -> "Riesgo de match por palabras clave";
            case "LEARNING_ROADMAP_ONLY" -> "Roadmap de aprendizaje";
            case "LOW_FIT" -> "Baja compatibilidad";
            default -> "No detectado";
        };
    }

    private static String labelEvidence(String code) {
        return switch (codeOrUnknown(code)) {
            case "WORK_EXPERIENCE" -> "Experiencia laboral";
            case "PROJECT" -> "Proyecto";
            case "ACADEMIC" -> "Academica";
            case "CERTIFICATION" -> "Certificacion";
            case "MENTIONED_ONLY" -> "Solo mencionada";
            case "TRANSFERABLE" -> "Transferible";
            case "NO_EVIDENCE" -> "Sin evidencia suficiente";
            default -> "No detectado";
        };
    }

    private static String labelConfidence(String code) {
        return switch (codeOrUnknown(code)) {
            case "HIGH" -> "Alta";
            case "MEDIUM" -> "Media";
            case "LOW" -> "Baja";
            default -> "No detectada";
        };
    }

    private static String labelRole(String code) {
        return switch (codeOrUnknown(code)) {
            case "BACKEND" -> "Backend";
            case "DOTNET_BACKEND" -> ".NET backend";
            case "FRONTEND" -> "Frontend";
            case "FULL_STACK" -> "Full stack";
            case "DOTNET_FULLSTACK" -> ".NET full stack";
            case "DATA" -> "Data/BI";
            case "DATABASE" -> "Base de datos";
            case "IT_SUPPORT" -> "Soporte IT";
            case "APP_SUPPORT" -> "Soporte de aplicaciones";
            case "CLOUD" -> "Cloud";
            case "DEVOPS" -> "DevOps";
            case "QA" -> "QA";
            case "SECURITY_OPS", "IAM" -> "Seguridad/IAM";
            default -> "No detectado";
        };
    }

    private static String labelSeniority(String code) {
        return switch (codeOrUnknown(code)) {
            case "TRAINEE" -> "Trainee";
            case "JUNIOR" -> "Junior";
            case "MID" -> "Semi senior";
            case "SENIOR" -> "Senior";
            case "LEAD" -> "Lead";
            default -> "No detectado";
        };
    }

    private static String labelSearchMode(String code) {
        return switch (codeOrUnknown(code)) {
            case "FOCUSED" -> "Enfocado";
            case "BALANCED" -> "Balanceado";
            case "EXPLORATORY" -> "Exploratorio";
            default -> "No detectado";
        };
    }

    private static String modeDetail(String code) {
        return switch (codeOrUnknown(code)) {
            case "BALANCED" -> "Se muestran senales de foco y oportunidades cercanas sin reordenar.";
            case "EXPLORATORY" -> "Se permite revisar oportunidades amplias sin filtrar resultados.";
            default -> "Se prioriza leer el foco declarado sin filtrar ni reordenar.";
        };
    }

    private static boolean rolesAligned(String targetRole, String detectedRole) {
        String target = normalizeRole(targetRole);
        String detected = normalizeRole(detectedRole);
        return !"UNKNOWN".equals(target) && target.equals(detected);
    }

    private static boolean isAdjacentRole(String targetRole, String detectedRole) {
        String target = normalizeRole(targetRole);
        String detected = normalizeRole(detectedRole);
        if ("UNKNOWN".equals(target) || "UNKNOWN".equals(detected) || target.equals(detected)) {
            return false;
        }
        return switch (target) {
            case "BACKEND" -> detected.equals("FULL_STACK") || detected.equals("DATABASE") || detected.equals("CLOUD") || detected.equals("DEVOPS");
            case "FRONTEND" -> detected.equals("FULL_STACK");
            case "FULL_STACK" -> detected.equals("BACKEND") || detected.equals("FRONTEND");
            case "DATA" -> detected.equals("DATABASE") || detected.equals("BACKEND");
            case "DATABASE" -> detected.equals("DATA") || detected.equals("BACKEND");
            case "IT_SUPPORT" -> detected.equals("APP_SUPPORT") || detected.equals("SECURITY_OPS") || detected.equals("IAM");
            case "APP_SUPPORT" -> detected.equals("IT_SUPPORT") || detected.equals("DATABASE");
            case "SECURITY_OPS", "IAM" -> detected.equals("IT_SUPPORT") || detected.equals("APP_SUPPORT") || detected.equals("CLOUD");
            case "DEVOPS", "CLOUD" -> detected.equals("BACKEND") || detected.equals("SECURITY_OPS");
            case "QA" -> detected.equals("BACKEND") || detected.equals("FULL_STACK");
            default -> false;
        };
    }

    private static String normalizeRole(String role) {
        return switch (codeOrUnknown(role)) {
            case "DOTNET_BACKEND" -> "BACKEND";
            case "DOTNET_FULLSTACK" -> "FULL_STACK";
            default -> codeOrUnknown(role);
        };
    }

    private static int seniorityRank(String seniority) {
        return switch (codeOrUnknown(seniority)) {
            case "TRAINEE" -> 1;
            case "JUNIOR" -> 2;
            case "MID" -> 3;
            case "SENIOR" -> 4;
            case "LEAD" -> 5;
            default -> 0;
        };
    }

    private static String labelBucket(String code) {
        return switch (codeOrUnknown(code)) {
            case "READY_NOW" -> "Listo para postular";
            case "GOOD_WITH_MINOR_GAPS" -> "Bueno con brechas menores";
            case "TRANSFERABLE" -> "Transferible";
            case "ASPIRATIONAL" -> "Aspiracional";
            case "WEAK_MATCH" -> "Match debil";
            case "LOW_FIT" -> "Baja compatibilidad";
            default -> "N/A";
        };
    }

    private static String labelTransferStrength(String code) {
        return switch (codeOrUnknown(code)) {
            case "STRONG" -> "Fuerte";
            case "PARTIAL" -> "Parcial";
            default -> "No detectado";
        };
    }

    private static String labelPolarity(String code) {
        return switch (codeOrUnknown(code)) {
            case "POSITIVE" -> "Positiva";
            case "NEGATIVE" -> "Negativa";
            case "NEUTRAL" -> "Neutral";
            default -> "No detectado";
        };
    }
}
