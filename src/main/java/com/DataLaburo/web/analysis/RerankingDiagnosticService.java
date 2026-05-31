package com.DataLaburo.web.analysis;

import com.DataLaburo.web.service.SkillExtractionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RerankingDiagnosticService {
    private static final Set<String> GENERIC_MATCH_SKILLS = Set.of("sql", "rest", "git");

    public RerankingDiagnostic evaluate(VectorFirstCompatibilityResult result, CompatibilitySignalContext context) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RerankSignal> signals = new ArrayList<>();

        String role = normalize(result.detectedRole());
        int matchedCount = size(result.matchedSkills());
        int criticalGaps = size(result.missingCriticalSkills());
        int secondaryGaps = size(result.missingSecondarySkills());
        int seniorityDelta = seniorityDelta(context);
        boolean roleAligned = roleAligned(context, role);
        boolean rolePeripheral = rolePeripheral(context, role);
        boolean genericOnly = matchedCount > 0 && onlyGenericMatches(result.matchedSkills());
        boolean hasTransfer = result.transferableSkills() != null && !result.transferableSkills().isEmpty();
        boolean hasSkillEquivalence = result.skillEquivalenceSignals() != null
                && !result.skillEquivalenceSignals().isEmpty();
        boolean hasStrongTransfer = result.transferableSkills() != null
                && result.transferableSkills().stream().anyMatch(skill -> skill.strength() == TransferStrength.STRONG);
        boolean strongEvidence = result.evidenceLevel() == EvidenceLevel.WORK_EXPERIENCE
                || result.evidenceLevel() == EvidenceLevel.PROJECT
                || result.evidenceLevel() == EvidenceLevel.CERTIFICATION;
        boolean weakEvidence = result.evidenceLevel() == EvidenceLevel.NO_EVIDENCE
                || result.evidenceLevel() == EvidenceLevel.MENTIONED_ONLY;

        addSignals(
                result,
                context,
                reasons,
                warnings,
                signals,
                roleAligned,
                rolePeripheral,
                genericOnly,
                hasTransfer,
                hasSkillEquivalence,
                hasStrongTransfer,
                strongEvidence,
                weakEvidence,
                seniorityDelta,
                matchedCount,
                criticalGaps,
                secondaryGaps
        );

        CompatibilityBucket bucket = bucketFor(
                roleAligned,
                rolePeripheral,
                genericOnly,
                hasTransfer,
                hasStrongTransfer,
                strongEvidence,
                weakEvidence,
                seniorityDelta,
                matchedCount,
                criticalGaps,
                secondaryGaps,
                result.compatibilityCategory(),
                result.vectorSimilarity()
        );
        reasons.add("Bucket diagnostico asignado: " + bucket + ".");

        return new RerankingDiagnostic(
                bucket,
                distinct(reasons),
                distinct(warnings),
                distinctSignals(signals)
        );
    }

    public List<VectorFirstCompatibilityResult> assignSuggestedRanks(List<VectorFirstCompatibilityResult> vectorOrderedResults) {
        if (vectorOrderedResults == null || vectorOrderedResults.isEmpty()) {
            return List.of();
        }
        List<VectorFirstCompatibilityResult> suggestedOrder = vectorOrderedResults.stream()
                .sorted(Comparator
                        .comparingInt((VectorFirstCompatibilityResult result) -> bucketPriority(result.compatibilityBucket()))
                        .thenComparingInt(VectorFirstCompatibilityResult::vectorRank))
                .toList();

        List<VectorFirstCompatibilityResult> constrainedOrder = constrainedSuggestedOrder(vectorOrderedResults, suggestedOrder);

        Map<Integer, Integer> suggestedRankByVectorRank = new LinkedHashMap<>();
        for (int i = 0; i < constrainedOrder.size(); i++) {
            suggestedRankByVectorRank.put(constrainedOrder.get(i).vectorRank(), i + 1);
        }
        List<VectorFirstCompatibilityResult> out = new ArrayList<>();
        for (VectorFirstCompatibilityResult result : vectorOrderedResults) {
            int suggestedRank = suggestedRankByVectorRank.getOrDefault(result.vectorRank(), result.vectorRank());
            int rankDelta = result.vectorRank() - suggestedRank;
            List<String> reasons = new ArrayList<>(safeList(result.rerankReasons()));
            if (rankDelta != 0 && reasons.isEmpty()) {
                reasons.add("Se moveria por prioridad ordinal del bucket " + result.compatibilityBucket() + ".");
            }
            out.add(result.withSuggestedRerankRank(suggestedRank, rankDelta, reasons));
        }
        return out;
    }

    private static List<VectorFirstCompatibilityResult> constrainedSuggestedOrder(
            List<VectorFirstCompatibilityResult> vectorOrderedResults,
            List<VectorFirstCompatibilityResult> suggestedOrder
    ) {
        List<VectorFirstCompatibilityResult> working = new ArrayList<>(vectorOrderedResults);
        for (VectorFirstCompatibilityResult desired : suggestedOrder) {
            int currentIndex = indexByVectorRank(working, desired.vectorRank());
            while (currentIndex > 0) {
                VectorFirstCompatibilityResult previous = working.get(currentIndex - 1);
                if (compareForSuggestedOrder(desired, previous) >= 0) {
                    break;
                }
                int nextRank = currentIndex;
                int upwardMovement = desired.vectorRank() - nextRank;
                if (upwardMovement > maxSuggestedUpwardMovement(desired)) {
                    break;
                }
                working.set(currentIndex - 1, desired);
                working.set(currentIndex, previous);
                currentIndex--;
            }
        }
        return working;
    }

    private static int indexByVectorRank(List<VectorFirstCompatibilityResult> results, int vectorRank) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).vectorRank() == vectorRank) {
                return i;
            }
        }
        return -1;
    }

    private static int compareForSuggestedOrder(
            VectorFirstCompatibilityResult left,
            VectorFirstCompatibilityResult right
    ) {
        int bucketComparison = Integer.compare(
                bucketPriority(left.compatibilityBucket()),
                bucketPriority(right.compatibilityBucket())
        );
        if (bucketComparison != 0) {
            return bucketComparison;
        }
        return Integer.compare(left.vectorRank(), right.vectorRank());
    }

    private static int maxSuggestedUpwardMovement(VectorFirstCompatibilityResult result) {
        if (result == null || result.compatibilityBucket() == null) {
            return 0;
        }
        boolean roleWarning = hasRoleWarning(result) || "unknown".equals(normalize(result.detectedRole()));
        boolean genericOnly = onlyGenericMatches(result.matchedSkills());
        return switch (result.compatibilityBucket()) {
            case READY_NOW, GOOD_WITH_MINOR_GAPS -> Integer.MAX_VALUE;
            case TRANSFERABLE -> roleWarning ? 2 : Integer.MAX_VALUE;
            case ASPIRATIONAL -> roleWarning ? 1 : 4;
            case WEAK_MATCH -> (genericOnly || roleWarning) ? 0 : 2;
            case LOW_FIT -> 0;
        };
    }

    private static void addSignals(
            VectorFirstCompatibilityResult result,
            CompatibilitySignalContext context,
            List<String> reasons,
            List<String> warnings,
            List<RerankSignal> signals,
            boolean roleAligned,
            boolean rolePeripheral,
            boolean genericOnly,
            boolean hasTransfer,
            boolean hasSkillEquivalence,
            boolean hasStrongTransfer,
            boolean strongEvidence,
            boolean weakEvidence,
            int seniorityDelta,
            int matchedCount,
            int criticalGaps,
            int secondaryGaps
    ) {
        if (roleAligned) {
            addSignal(signals, "ROLE_ALIGNED", RerankSignalPolarity.POSITIVE,
                    "Rol " + result.detectedRole() + " alineado con el objetivo " + displayRole(profileRole(context)) + " del perfil.");
            reasons.add("Subiria o se mantendria por alineacion de rol: " + result.detectedRole()
                    + " con perfil " + displayRole(profileRole(context)) + ".");
        } else if (rolePeripheral) {
            addSignal(signals, "ROLE_PERIPHERAL", RerankSignalPolarity.NEGATIVE,
                    "Rol " + result.detectedRole() + " periferico respecto del objetivo " + displayRole(profileRole(context)) + ".");
            reasons.add("Bajaria por rol periferico: " + result.detectedRole()
                    + " no esta alineado con perfil " + displayRole(profileRole(context)) + ".");
        } else {
            addSignal(signals, "ROLE_UNCLEAR", RerankSignalPolarity.NEUTRAL,
                    "El rol detectado no alcanza para mover el ranking por si solo.");
        }

        if (seniorityDelta >= 2) {
            addSignal(signals, "SENIORITY_TOO_HIGH", RerankSignalPolarity.NEGATIVE,
                    "Oferta " + result.detectedSeniority() + " vs perfil " + context.profileSeniority() + ".");
            reasons.add("Bajaria por seniority superior: oferta " + result.detectedSeniority()
                    + " vs perfil " + context.profileSeniority() + ".");
        } else if (seniorityDelta == 1) {
            addSignal(signals, "SENIORITY_STRETCH", RerankSignalPolarity.NEGATIVE,
                    "La oferta requiere un nivel mas que el perfil.");
            reasons.add("Bajaria levemente por seniority un nivel superior.");
        } else if (seniorityDelta <= 0 && seniorityRank(result.detectedSeniority()) > 0) {
            addSignal(signals, "SENIORITY_COMPATIBLE", RerankSignalPolarity.POSITIVE,
                    "El seniority no supera al perfil.");
        }

        if (matchedCount == 0) {
            addSignal(signals, "NO_MATCHED_SKILLS", RerankSignalPolarity.NEGATIVE,
                    "No hay skills matcheadas entre perfil y oferta.");
            reasons.add("Bajaria por falta de skills matcheadas.");
        } else if (genericOnly) {
            addSignal(signals, "GENERIC_MATCHES_ONLY", RerankSignalPolarity.NEGATIVE,
                    "Los matches son solo SQL, REST o Git.");
            reasons.add("Bajaria porque los matches genericos no son suficientes: "
                    + String.join(", ", result.matchedSkills()) + ".");
        } else {
            addSignal(signals, "SPECIFIC_MATCHES_PRESENT", RerankSignalPolarity.POSITIVE,
                    "Hay skills especificas compartidas.");
            reasons.add("Subiria o se mantendria por skills especificas compartidas.");
        }

        if (criticalGaps > 0) {
            addSignal(signals, "CRITICAL_GAPS", RerankSignalPolarity.NEGATIVE,
                    "Gaps criticos detectados: " + criticalGaps + ".");
            reasons.add("Bajaria por gaps criticos: " + String.join(", ", result.missingCriticalSkills()) + ".");
        } else {
            addSignal(signals, "NO_CRITICAL_GAPS", RerankSignalPolarity.POSITIVE,
                    "No hay gaps criticos detectados.");
        }

        if (secondaryGaps > 0 && criticalGaps == 0) {
            addSignal(signals, "SECONDARY_GAPS_ONLY", RerankSignalPolarity.NEUTRAL,
                    "Solo hay gaps secundarios.");
        }

        if (hasStrongTransfer) {
            addSignal(signals, "STRONG_TRANSFERABILITY", RerankSignalPolarity.POSITIVE,
                    "Hay transferencia fuerte defendible.");
            reasons.add("Se mantendria por transferibilidad fuerte y defendible.");
        } else if (hasTransfer) {
            addSignal(signals, "PARTIAL_TRANSFERABILITY", RerankSignalPolarity.NEUTRAL,
                    "La transferencia es parcial o complementaria.");
        }

        if (hasSkillEquivalence) {
            addSignal(signals, "SKILL_EQUIVALENCE_DIAGNOSTIC", RerankSignalPolarity.NEUTRAL,
                    skillEquivalenceDetail(result.skillEquivalenceSignals()));
            reasons.add("Hay equivalencias o relaciones parciales de skills; se informan sin cambiar gaps ni ranking.");
        }

        if (strongEvidence) {
            addSignal(signals, "STRONG_EVIDENCE", RerankSignalPolarity.POSITIVE,
                    "Nivel de evidencia: " + result.evidenceLevel() + ".");
        } else if (weakEvidence) {
            addSignal(signals, "WEAK_EVIDENCE", RerankSignalPolarity.NEGATIVE,
                    "Nivel de evidencia debil: " + result.evidenceLevel() + ".");
            reasons.add("Bajaria por evidencia debil: " + result.evidenceLevel() + ".");
        }

        addSignal(signals, "VECTOR_SIMILARITY", RerankSignalPolarity.NEUTRAL,
                "La similitud vectorial justifica el candidato, pero no decide compatibilidad profesional.");

        addRoleWarnings(result, warnings);
    }

    private static CompatibilityBucket bucketFor(
            boolean roleAligned,
            boolean rolePeripheral,
            boolean genericOnly,
            boolean hasTransfer,
            boolean hasStrongTransfer,
            boolean strongEvidence,
            boolean weakEvidence,
            int seniorityDelta,
            int matchedCount,
            int criticalGaps,
            int secondaryGaps,
            CompatibilityCategory category,
            double vectorSimilarity
    ) {
        boolean alignedProjectMatch = roleAligned
                && matchedCount >= 2
                && !genericOnly
                && strongEvidence
                && criticalGaps == 0;
        if ((rolePeripheral && (genericOnly || !hasStrongTransfer))
                || (seniorityDelta >= 3 && !alignedProjectMatch)
                || matchedCount == 0
                || (genericOnly && criticalGaps > 0)) {
            return CompatibilityBucket.LOW_FIT;
        }
        if (genericOnly || weakEvidence || (criticalGaps >= 3 && !hasStrongTransfer)) {
            return CompatibilityBucket.WEAK_MATCH;
        }
        if (roleAligned && seniorityDelta <= 0 && criticalGaps == 0 && matchedCount >= 2 && strongEvidence) {
            return secondaryGaps == 0
                    ? CompatibilityBucket.READY_NOW
                    : CompatibilityBucket.GOOD_WITH_MINOR_GAPS;
        }
        if (roleAligned && criticalGaps == 0 && matchedCount > 0 && seniorityDelta <= 1) {
            return CompatibilityBucket.GOOD_WITH_MINOR_GAPS;
        }
        if ((hasStrongTransfer || hasTransfer) && (roleAligned || matchedCount > 0 || vectorSimilarity >= 0.50d)) {
            return CompatibilityBucket.TRANSFERABLE;
        }
        if (seniorityDelta >= 2
                || category == CompatibilityCategory.ASPIRATIONAL_MATCH
                || category == CompatibilityCategory.LEARNING_ROADMAP_ONLY
                || criticalGaps > 0
                || vectorSimilarity >= 0.55d) {
            return CompatibilityBucket.ASPIRATIONAL;
        }
        return CompatibilityBucket.WEAK_MATCH;
    }

    private static void addRoleWarnings(VectorFirstCompatibilityResult result, List<String> warnings) {
        String title = normalize(result.title());
        String role = normalize(result.detectedRole());
        if (title.contains("android") && "qa".equals(role)) {
            warnings.add("Posible deteccion dudosa de rol: titulo Android clasificado como QA.");
        }
        if ((title.contains("customer success") || title.contains("training")) && "security_ops".equals(role)) {
            warnings.add("Posible deteccion dudosa de rol: Customer Success/Training clasificado como SECURITY_OPS.");
        }
        if ("unknown".equals(role)) {
            warnings.add("Rol no determinado; no conviene usar reranking para tapar este caso.");
        }
    }

    private static String skillEquivalenceDetail(List<SkillEquivalenceSignal> skillEquivalenceSignals) {
        if (skillEquivalenceSignals == null || skillEquivalenceSignals.isEmpty()) {
            return "No hay equivalencias parciales detectadas.";
        }
        SkillEquivalenceSignal first = skillEquivalenceSignals.get(0);
        String detail = first.candidateSkill() + " -> " + first.targetSkill() + " (" + first.relation() + ")";
        if (skillEquivalenceSignals.size() > 1) {
            detail = detail + " y " + (skillEquivalenceSignals.size() - 1) + " relacion(es) mas";
        }
        return detail + ". Diagnostico solamente; no modifica ranking.";
    }

    private static boolean hasRoleWarning(VectorFirstCompatibilityResult result) {
        if (result == null || result.rerankWarnings() == null) {
            return false;
        }
        return result.rerankWarnings().stream()
                .map(RerankingDiagnosticService::normalize)
                .anyMatch(warning -> warning.contains("rol") || warning.contains("role"));
    }

    private static boolean onlyGenericMatches(List<String> matchedSkills) {
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return false;
        }
        for (String skill : matchedSkills) {
            if (!GENERIC_MATCH_SKILLS.contains(normalize(skill))) {
                return false;
            }
        }
        return true;
    }

    private static int seniorityDelta(CompatibilitySignalContext context) {
        if (context == null) {
            return 0;
        }
        int jobRank = seniorityRank(context.detectedSeniority());
        int profileRank = seniorityRank(context.profileSeniority());
        if (jobRank <= 0 || profileRank <= 0) {
            return 0;
        }
        return jobRank - profileRank;
    }

    private static int seniorityRank(String seniority) {
        return switch (normalize(seniority)) {
            case "trainee" -> 1;
            case "junior" -> 2;
            case "mid", "semi_senior", "semisenior", "ssr" -> 3;
            case "senior", "sr" -> 4;
            case "lead" -> 5;
            default -> 0;
        };
    }

    private static boolean roleAligned(CompatibilitySignalContext context, String jobRole) {
        String profileRole = profileRole(context);
        return switch (profileRole) {
            case "backend" -> Set.of("backend", "full_stack", "dotnet_backend", "dotnet_fullstack", "database").contains(jobRole);
            case "frontend" -> Set.of("frontend", "full_stack", "dotnet_fullstack").contains(jobRole);
            case "data" -> Set.of("data", "database").contains(jobRole);
            case "it_support", "app_support" -> Set.of("it_support", "app_support").contains(jobRole);
            case "cloud", "devops" -> Set.of("cloud", "devops", "backend").contains(jobRole);
            case "qa" -> "qa".equals(jobRole);
            case "security_ops", "iam" -> Set.of("security_ops", "iam").contains(jobRole);
            default -> false;
        };
    }

    private static boolean rolePeripheral(CompatibilitySignalContext context, String jobRole) {
        String profileRole = profileRole(context);
        if (jobRole == null || jobRole.isBlank() || "unknown".equals(jobRole)) {
            return false;
        }
        return !profileRole.isBlank() && !roleAligned(context, jobRole);
    }

    private static String profileRole(CompatibilitySignalContext context) {
        String role = normalize(context == null ? null : context.profileRole());
        return role.isBlank() ? "backend" : role;
    }

    private static String displayRole(String role) {
        String normalized = normalize(role);
        return normalized.isBlank() ? "BACKEND" : normalized.toUpperCase();
    }

    private static int bucketPriority(CompatibilityBucket bucket) {
        if (bucket == null) {
            return Integer.MAX_VALUE;
        }
        return switch (bucket) {
            case READY_NOW -> 1;
            case GOOD_WITH_MINOR_GAPS -> 2;
            case TRANSFERABLE -> 3;
            case ASPIRATIONAL -> 4;
            case WEAK_MATCH -> 5;
            case LOW_FIT -> 6;
        };
    }

    private static void addSignal(
            List<RerankSignal> signals,
            String name,
            RerankSignalPolarity polarity,
            String detail
    ) {
        signals.add(new RerankSignal(name, polarity, detail));
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> distinct(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<RerankSignal> distinctSignals(List<RerankSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        Map<String, RerankSignal> byName = new LinkedHashMap<>();
        for (RerankSignal signal : signals) {
            if (signal != null && signal.name() != null && !signal.name().isBlank()) {
                byName.putIfAbsent(signal.name(), signal);
            }
        }
        return List.copyOf(byName.values());
    }

    private static String normalize(String value) {
        return SkillExtractionService.normalizeText(value).replace(' ', '_');
    }
}
