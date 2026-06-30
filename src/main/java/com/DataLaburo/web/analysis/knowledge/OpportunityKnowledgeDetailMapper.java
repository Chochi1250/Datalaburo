package com.DataLaburo.web.analysis.knowledge;

import com.DataLaburo.web.analysis.VectorFirstCompatibilityResult;
import com.DataLaburo.web.analysis.evidence.ProfessionalDomain;
import com.DataLaburo.web.analysis.evidence.ProfessionalEvidenceType;
import com.DataLaburo.web.analysis.evidence.ProfileEvidenceSummary;
import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class OpportunityKnowledgeDetailMapper {
    private static final int MIN_RELIABLE_DESCRIPTION_CHARS = 120;
    private static final int MIN_RELIABLE_DESCRIPTION_WORDS = 20;
    private static final String TECHNICAL_MATCH_LIMIT_NOTE =
            "Hay coincidencias tecnicas sin evidencia visible; no se presentan como fortaleza profesional.";
    private static final String OUT_OF_SCOPE_SHARED_SIGNAL_WARNING =
            "No demuestran experiencia en el dominio de la oferta.";

    private final KnowledgeCatalogResolver resolver;

    public OpportunityKnowledgeDetailMapper(KnowledgeCatalogResolver resolver) {
        this.resolver = resolver;
    }

    public OpportunityKnowledgeDetailView map(
            CandidateProfile profile,
            Job job,
            VectorFirstCompatibilityResult result,
            ProfileEvidenceSummary profileEvidence
    ) {
        Objects.requireNonNull(result, "result must not be null");

        OpportunityRoleResolution opportunityRole = resolveOpportunityRole(result.detectedRole(), job);
        String opportunityText = opportunityText(job);
        OpportunityKnowledgeEnrichment enrichment = resolver.resolve(new KnowledgeResolutionInput(
                resolveProfileRole(profile, profileEvidence),
                opportunityRole.primaryRole(),
                opportunityRole.secondaryRole(),
                result.detectedSeniority(),
                safe(result.matchedSkills()),
                safe(result.missingCriticalSkills()),
                safe(result.missingSecondarySkills()),
                profileEvidence == null ? List.of() : profileEvidence.skillEvidence(),
                hasInsufficientMetadata(job)
        ));

        boolean hasHiddenTechnicalMatches = enrichment.strengths().stream()
                .anyMatch(strength -> !isVisibleStrength(strength));
        List<OpportunityKnowledgeDetailView.StrengthItem> strengths = enrichment.strengths().stream()
                .filter(OpportunityKnowledgeDetailMapper::isVisibleStrength)
                .limit(4)
                .map(OpportunityKnowledgeDetailMapper::toStrength)
                .toList();
        List<OpportunityKnowledgeEnrichment.Gap> resolvedGaps = prioritizedGaps(enrichment.gaps(), opportunityText);
        List<ActionDraft> actionDrafts = prioritizedActions(enrichment.actions(), resolvedGaps, enrichment.transfers());
        Map<String, String> actionByTechnology = actionByTechnology(actionDrafts);

        List<OpportunityKnowledgeDetailView.GapItem> gaps = new ArrayList<>();
        for (int index = 0; index < resolvedGaps.size(); index++) {
            OpportunityKnowledgeEnrichment.Gap gap = resolvedGaps.get(index);
            gaps.add(new OpportunityKnowledgeDetailView.GapItem(
                    gap.technologyLabel(),
                    gap.severity() == OpportunityKnowledgeEnrichment.GapSeverity.CRITICAL ? "Alta" : "Media",
                    gap.severity().name(),
                    gap.explanation(),
                    transferNote(gap, enrichment.transfers(), index),
                    actionByTechnology.get(gap.technologyId())
            ));
        }

        List<OpportunityKnowledgeDetailView.TransferItem> transfers = enrichment.transfers().stream()
                .limit(3)
                .map(transfer -> new OpportunityKnowledgeDetailView.TransferItem(
                        routeLabel(transfer.fromRoleId(), transfer.toRoleId()),
                        transfer.transferableConcepts(),
                        transfer.warning()
                ))
                .toList();

        Map<String, OpportunityKnowledgeDetailView.ActionItem> actions = new LinkedHashMap<>();
        for (ActionDraft draft : actionDrafts) {
            actions.putIfAbsent(draft.intentionKey(), draft.item());
        }

        String coverageCode = enrichment.coverageLevel().name();
        String roleFamilyLabel = enrichment.roleFamily() == null ? null : enrichment.roleFamily().label();
        OpportunityKnowledgeEnrichment.SecondaryFocus secondaryFocus = enrichment.secondaryFocus();
        List<OpportunityKnowledgeDetailView.SharedSignalItem> sharedSignals =
                enrichment.coverageLevel() == OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE
                        ? sharedSignals(result.matchedSkills(), profileEvidence)
                        : List.of();
        return new OpportunityKnowledgeDetailView(
                coverageCode,
                coverageLabel(enrichment.coverageLevel()),
                roleFamilyLabel,
                secondaryFocus == null ? null : secondaryFocus.label(),
                secondaryFocus == null ? null : secondaryFocus.limit(),
                summary(enrichment.coverageLevel(), roleFamilyLabel),
                hasHiddenTechnicalMatches ? TECHNICAL_MATCH_LIMIT_NOTE : null,
                sharedSignals,
                strengths,
                List.copyOf(gaps),
                transfers,
                actions.values().stream().limit(3).toList(),
                enrichment.coverageLevel() == OpportunityKnowledgeEnrichment.CoverageLevel.LOW_CONTEXT,
                enrichment.coverageLevel() == OpportunityKnowledgeEnrichment.CoverageLevel.OUT_OF_SCOPE
        );
    }

    static boolean hasInsufficientMetadata(Job job) {
        if (job == null) {
            return true;
        }
        return !isSubstantial(job.getRequirementsText()) && !isSubstantial(job.getDescription());
    }

    private static boolean isSubstantial(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String plain = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        String normalized = KnowledgeCatalogValidator.normalize(plain);
        if (normalized.equals("acerca del empleo") || normalized.equals("about the job")) {
            return false;
        }
        return plain.length() >= MIN_RELIABLE_DESCRIPTION_CHARS
                && plain.split("\\s+").length >= MIN_RELIABLE_DESCRIPTION_WORDS;
    }

    private static String resolveProfileRole(CandidateProfile profile, ProfileEvidenceSummary evidence) {
        String target = profile == null ? null : profile.getTargetRole();
        String fallbackRole = usableTargetRole(target);
        if (evidence == null) {
            return fallbackRole;
        }
        String workRole = roleFromEvidence(
                evidence.skillEvidence(),
                List.of(ProfessionalEvidenceType.WORK_EXPERIENCE),
                fallbackRole
        );
        if (workRole != null) {
            return workRole;
        }
        String projectRole = roleFromEvidence(
                evidence.skillEvidence(),
                List.of(ProfessionalEvidenceType.PROJECT),
                fallbackRole
        );
        if (projectRole != null) {
            return projectRole;
        }
        String academicRole = roleFromEvidence(
                evidence.skillEvidence(),
                List.of(ProfessionalEvidenceType.ACADEMIC),
                fallbackRole
        );
        if (academicRole != null) {
            return academicRole;
        }
        String declaredRole = roleFromEvidence(
                evidence.skillEvidence(),
                List.of(ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalEvidenceType.TRANSFERABLE),
                fallbackRole
        );
        return declaredRole == null ? fallbackRole : declaredRole;
    }

    static OpportunityRoleResolution resolveOpportunityRole(String detectedRole, Job job) {
        String title = KnowledgeCatalogValidator.normalize(job == null ? null : job.getTitle());
        String details = KnowledgeCatalogValidator.normalize(job == null
                ? null
                : String.join(" ", safe(job.getDescription()), safe(job.getRequirementsText())));
        String fullText = String.join(" ", title, details).trim();
        String excluded = deliberateOutOfScopeRole(title);
        if (excluded != null) {
            return new OpportunityRoleResolution(excluded, null);
        }
        String primary = explicitTitleRole(title, fullText);
        if (primary == null) {
            primary = explicitRoleFromFullText(title, fullText);
        }
        if (primary == null) {
            primary = coherentTechnologyRole(fullText);
        }
        if (primary == null) {
            primary = mapRoleCode(code(detectedRole));
        }
        String secondary = secondaryOpportunityFocus(primary, title, fullText);
        return new OpportunityRoleResolution(primary, secondary);
    }

    private static String explicitTitleRole(String title, String fullText) {
        if (containsCloudDevopsTitle(title)) {
            return "CLOUD_DEVOPS";
        }
        if (containsBackendTitle(title)) {
            return "BACKEND";
        }
        if (containsAppSupportTitle(title)) {
            return "APP_SUPPORT_OPERATIONS";
        }
        if (containsItSupportTitle(title)) {
            return "IT_SUPPORT";
        }
        if (containsFullStackTitle(title)) {
            return "WEB_FULL_STACK";
        }
        if (containsFrontendTitle(title)) {
            return "WEB_FRONTEND";
        }
        if (containsPhrase(title, "data support") || containsPhrase(title, "analytics support")) {
            return "DATA";
        }
        if (containsPhrase(title, "data engineer") || containsPhrase(title, "ingeniero de datos")
                || containsPhrase(title, "big data engineer") || containsPhrase(title, "cloud data engineer")) {
            return "DATA_ENGINEERING";
        }
        if (containsPhrase(title, "machine learning") || containsPhrase(title, "ml engineer")
                || containsPhrase(title, "ai engineer") || containsPhrase(title, "software engineer ai")
                || containsPhrase(title, "artificial intelligence")) {
            return "AI_ML_APPLIED";
        }
        if (containsPhrase(title, "database") || containsPhrase(title, "base de datos")
                || containsPhrase(title, "dba")) {
            return "DATABASE_ENGINEERING";
        }
        if (containsPhrase(title, "infrastructure") || containsPhrase(title, "infraestructura")
                || containsPhrase(title, "network engineer")) {
            return "INFRASTRUCTURE_NETWORKS";
        }
        if (containsPhrase(title, "security") || containsPhrase(title, "appsec")) {
            return securityRole(fullText);
        }
        return null;
    }

    private static String explicitRoleFromFullText(String title, String fullText) {
        if (containsCloudDevopsRole(fullText)) {
            return "CLOUD_DEVOPS";
        }
        if (containsFullStackRole(fullText)) {
            return "WEB_FULL_STACK";
        }
        if (containsBackendRole(fullText)) {
            return "BACKEND";
        }
        if (containsAppSupportRole(fullText)) {
            return "APP_SUPPORT_OPERATIONS";
        }
        if (containsItSupportRole(fullText)) {
            return "IT_SUPPORT";
        }
        if (containsFrontendRole(fullText)) {
            return "WEB_FRONTEND";
        }
        if (containsPhrase(fullText, "data engineer") || containsPhrase(fullText, "ingeniero de datos")) {
            return "DATA_ENGINEERING";
        }
        if (containsPhrase(fullText, "business intelligence") || containsPhrase(fullText, "power bi")
                || containsPhrase(fullText, "data analyst")) {
            return "DATA";
        }
        if (containsSecurityRole(fullText)) {
            return securityRole(fullText);
        }
        return null;
    }

    private static String coherentTechnologyRole(String fullText) {
        int cloudSignals = countPhrases(
                fullText,
                "aws", "azure", "gcp", "kubernetes", "openshift", "terraform", "ci cd",
                "github actions", "jenkins", "helm", "argo cd"
        );
        if (cloudSignals >= 3 && containsAnyPhrase(fullText, "devops", "cloud", "infrastructure", "platform")) {
            return "CLOUD_DEVOPS";
        }

        int frontendSignals = countPhrases(
                fullText,
                "react", "vue", "angular", "javascript", "typescript", "html", "css"
        );
        int backendSignals = countPhrases(
                fullText,
                "java", "spring boot", "kotlin", "node js", "node", "rest api", "api rest",
                "mysql", "postgresql", "sql"
        );
        if (frontendSignals >= 1 && backendSignals >= 2) {
            return "WEB_FULL_STACK";
        }
        if (frontendSignals >= 2 && containsAnyPhrase(fullText, "frontend", "web", "ui")) {
            return "WEB_FRONTEND";
        }
        if (backendSignals >= 3 && containsAnyPhrase(fullText, "backend", "api", "microservices", "software developer")) {
            return "BACKEND";
        }

        int dataSignals = countPhrases(fullText, "sql", "power bi", "etl", "spark", "databricks", "snowflake", "bigquery");
        if (dataSignals >= 3 && containsAnyPhrase(fullText, "data", "analytics", "bi", "pipeline")) {
            return "DATA";
        }
        return null;
    }

    private static String secondaryOpportunityFocus(String primaryRole, String title, String fullText) {
        if (primaryRole == null || explicitOutOfScopeRoles(primaryRole)) {
            return null;
        }
        if (!"SECURITY_ENGINEERING".equals(primaryRole) && containsSecurityRole(fullText)) {
            return securityRole(fullText);
        }
        if (!"APP_SUPPORT_OPERATIONS".equals(primaryRole) && containsAppSupportRole(fullText)) {
            return "APP_SUPPORT_OPERATIONS";
        }
        if (!"CLOUD_DEVOPS".equals(primaryRole) && containsCloudDevopsRole(fullText)
                && !containsPhrase(title, "platform security")) {
            return "CLOUD_DEVOPS";
        }
        return null;
    }

    private static String deliberateOutOfScopeRole(String title) {
        if (containsPhrase(title, "sap s2c")) return "sap";
        if (containsPhrase(title, "sap business analyst")) return "sap";
        if (containsPhrase(title, "sap")) return "sap";
        if (containsPhrase(title, "s2c")) return "sap";
        if (containsPhrase(title, "erp functional")) return "erp";
        if (containsPhrase(title, "salesforce administrator")) return "salesforce";
        if (containsPhrase(title, "crm functional")) return "crm";
        if (containsPhrase(title, "customer success")) return "customer success";
        if (containsPhrase(title, "mobile developer")) return "mobile developer";
        if (containsPhrase(title, "android developer")) return "android developer";
        if (containsPhrase(title, "ios developer")) return "ios developer";
        if (containsPhrase(title, "embedded")) return "embedded";
        if (containsPhrase(title, "game developer")) return "game developer";
        if (containsPhrase(title, "ux designer")) return "ux designer";
        if (containsPhrase(title, "product designer")) return "product designer";
        if (containsPhrase(title, "sales engineer")) return "sales engineer";
        return null;
    }

    private static boolean explicitOutOfScopeRoles(String role) {
        String normalized = code(role);
        return normalized.isBlank()
                || normalized.equals("CUSTOMER_SUCCESS")
                || normalized.equals("MOBILE_DEVELOPER")
                || normalized.equals("ANDROID_DEVELOPER")
                || normalized.equals("IOS_DEVELOPER")
                || normalized.equals("EMBEDDED")
                || normalized.equals("GAME_DEVELOPER")
                || normalized.equals("UX_DESIGNER")
                || normalized.equals("PRODUCT_DESIGNER")
                || normalized.equals("SALES_ENGINEER");
    }

    private static String usableTargetRole(String target) {
        if (target == null || target.isBlank() || "UNDECIDED".equalsIgnoreCase(target)) {
            return null;
        }
        return mapRoleCode(target);
    }

    private static String roleFromEvidence(
            List<com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence> evidence,
            List<ProfessionalEvidenceType> acceptedTypes,
            String fallbackRole
    ) {
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        for (com.DataLaburo.web.analysis.evidence.ProfessionalSkillEvidence item : safe(evidence)) {
            if (!acceptedTypes.contains(item.evidenceType())) {
                continue;
            }
            String role = roleForDomain(item.domain());
            if (role == null) {
                continue;
            }
            roleCounts.merge(role, 1, Integer::sum);
        }
        if (roleCounts.isEmpty()) {
            return null;
        }
        if (fallbackRole != null && roleCounts.containsKey(fallbackRole)) {
            return fallbackRole;
        }
        if ("IT_SUPPORT".equals(fallbackRole) && roleCounts.containsKey("INFRASTRUCTURE_NETWORKS")) {
            return "IT_SUPPORT";
        }
        return roleCounts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(entry -> rolePriority(entry.getKey())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static String mapRoleCode(String value) {
        return switch (code(value)) {
            case "BACKEND", "DOTNET_BACKEND" -> "BACKEND";
            case "DATA" -> "DATA";
            case "IT_SUPPORT" -> "IT_SUPPORT";
            case "CLOUD", "DEVOPS", "CLOUD_DEVOPS" -> "CLOUD_DEVOPS";
            case "APP_SUPPORT", "APP_SUPPORT_OPERATIONS" -> "APP_SUPPORT_OPERATIONS";
            case "IAM", "SECURITY_IAM" -> "SECURITY_IAM";
            case "FULL_STACK", "DOTNET_FULLSTACK", "WEB_FULL_STACK" -> "WEB_FULL_STACK";
            case "FRONTEND", "WEB_FRONTEND" -> "WEB_FRONTEND";
            case "DATA_ENGINEERING" -> "DATA_ENGINEERING";
            case "QA", "QA_AUTOMATION" -> "QA_AUTOMATION";
            case "INFRA", "INFRASTRUCTURE", "INFRASTRUCTURE_NETWORKS" -> "INFRASTRUCTURE_NETWORKS";
            case "DATABASE", "DATABASE_ENGINEERING" -> "DATABASE_ENGINEERING";
            case "SECURITY", "SECURITY_OPS", "SECURITY_ENGINEERING" -> "SECURITY_ENGINEERING";
            case "AI", "AI_ML", "AI_ML_APPLIED" -> "AI_ML_APPLIED";
            default -> value;
        };
    }

    private static String roleForDomain(ProfessionalDomain domain) {
        if (domain == null) return null;
        return switch (domain) {
            case BACKEND_JAVA, BACKEND_DOTNET -> "BACKEND";
            case SUPPORT -> "IT_SUPPORT";
            case APP_SUPPORT -> "APP_SUPPORT_OPERATIONS";
            case INFRA -> "INFRASTRUCTURE_NETWORKS";
            case CLOUD -> "CLOUD_DEVOPS";
            case DATA -> "DATA";
            case FRONTEND -> "WEB_FRONTEND";
            case QA -> "QA_AUTOMATION";
            case SECURITY -> "SECURITY_ENGINEERING";
            case UNKNOWN -> null;
        };
    }

    private static int rolePriority(String role) {
        return switch (code(role)) {
            case "BACKEND" -> 90;
            case "IT_SUPPORT" -> 80;
            case "CLOUD_DEVOPS" -> 75;
            case "APP_SUPPORT_OPERATIONS" -> 70;
            case "DATA" -> 65;
            case "WEB_FULL_STACK" -> 60;
            case "WEB_FRONTEND" -> 55;
            case "INFRASTRUCTURE_NETWORKS" -> 50;
            case "SECURITY_ENGINEERING", "SECURITY_IAM" -> 45;
            default -> 10;
        };
    }

    private static boolean isVisibleStrength(OpportunityKnowledgeEnrichment.Strength strength) {
        return strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.STRONG
                || strength.evidenceAssessment() == OpportunityKnowledgeEnrichment.EvidenceAssessment.SUPPORTING;
    }

    private static List<OpportunityKnowledgeEnrichment.Gap> prioritizedGaps(
            List<OpportunityKnowledgeEnrichment.Gap> gaps,
            String opportunityText
    ) {
        List<OpportunityKnowledgeEnrichment.Gap> out = new ArrayList<>();
        for (OpportunityKnowledgeEnrichment.Gap gap : gaps) {
            if (gap.severity() != OpportunityKnowledgeEnrichment.GapSeverity.CRITICAL) {
                continue;
            }
            out.add(gap);
            if (out.size() == 2) {
                break;
            }
        }
        if (out.size() >= 3) {
            return List.copyOf(out);
        }
        for (OpportunityKnowledgeEnrichment.Gap gap : gaps) {
            if (gap.severity() != OpportunityKnowledgeEnrichment.GapSeverity.SECONDARY) {
                continue;
            }
            if (isContextOnlyGap(gap) || !isLiteralGap(gap, opportunityText)) {
                continue;
            }
            out.add(gap);
            break;
        }
        return List.copyOf(out);
    }

    private static boolean isContextOnlyGap(OpportunityKnowledgeEnrichment.Gap gap) {
        String explanation = KnowledgeCatalogValidator.normalize(gap.explanation());
        return explanation.contains("contexto complementario")
                || explanation.contains("no se presume como requisito");
    }

    private static boolean isLiteralGap(OpportunityKnowledgeEnrichment.Gap gap, String opportunityText) {
        if (containsPhrase(opportunityText, gap.technologyLabel())) {
            return true;
        }
        return gap.sourceSkills().stream().anyMatch(signal -> containsPhrase(opportunityText, signal));
    }

    private static List<ActionDraft> prioritizedActions(
            List<OpportunityKnowledgeEnrichment.Action> actions,
            List<OpportunityKnowledgeEnrichment.Gap> prioritizedGaps,
            List<OpportunityKnowledgeEnrichment.Transfer> transfers
    ) {
        Map<String, ActionDraft> out = new LinkedHashMap<>();

        if (!prioritizedGaps.isEmpty()) {
            OpportunityKnowledgeEnrichment.Gap firstGap = prioritizedGaps.get(0);
            OpportunityKnowledgeEnrichment.Action gapAction = firstActionForTechnology(actions, firstGap.technologyId());
            if (gapAction != null) {
                addAction(out, gapAction, firstGap.technologyLabel());
            }
        }

        OpportunityKnowledgeEnrichment.Action transferAction = firstActionByReason(actions, "transferencia");
        if (transferAction == null && !transfers.isEmpty()) {
            transferAction = firstActionWithoutTechnology(actions);
        }
        if (transferAction != null) {
            addAction(out, transferAction, "Transicion");
        } else {
            OpportunityKnowledgeEnrichment.Action evidenceAction = firstActionByReason(actions, "evidencia");
            if (evidenceAction != null) {
                addAction(out, evidenceAction, "Evidencia visible");
            }
        }

        OpportunityKnowledgeEnrichment.Gap secondaryGap = firstSecondaryGap(prioritizedGaps);
        if (secondaryGap != null) {
            OpportunityKnowledgeEnrichment.Action secondaryAction = firstActionForTechnology(actions, secondaryGap.technologyId());
            if (secondaryAction != null) {
                addAction(out, secondaryAction, secondaryGap.technologyLabel());
            }
        }

        return out.values().stream().limit(3).toList();
    }

    private static OpportunityKnowledgeEnrichment.Gap firstSecondaryGap(
            List<OpportunityKnowledgeEnrichment.Gap> prioritizedGaps
    ) {
        for (OpportunityKnowledgeEnrichment.Gap gap : prioritizedGaps) {
            if (gap.severity() == OpportunityKnowledgeEnrichment.GapSeverity.SECONDARY) {
                return gap;
            }
        }
        return null;
    }

    private static Map<String, String> actionByTechnology(List<ActionDraft> drafts) {
        Map<String, String> out = new LinkedHashMap<>();
        for (ActionDraft draft : drafts) {
            if (draft.technologyId() == null || draft.technologyId().isBlank()) {
                continue;
            }
            out.putIfAbsent(draft.technologyId(), draft.item().text());
        }
        return out;
    }

    private static void addAction(
            Map<String, ActionDraft> out,
            OpportunityKnowledgeEnrichment.Action action,
            String title
    ) {
        String key = actionIntentionKey(action);
        out.putIfAbsent(key, new ActionDraft(
                action.technologyId(),
                key,
                new OpportunityKnowledgeDetailView.ActionItem(title, action.text(), action.reason())
        ));
    }

    private static OpportunityKnowledgeEnrichment.Action firstActionForTechnology(
            List<OpportunityKnowledgeEnrichment.Action> actions,
            String technologyId
    ) {
        for (OpportunityKnowledgeEnrichment.Action action : actions) {
            if (Objects.equals(action.technologyId(), technologyId)) {
                return action;
            }
        }
        return null;
    }

    private static OpportunityKnowledgeEnrichment.Action firstActionByReason(
            List<OpportunityKnowledgeEnrichment.Action> actions,
            String reason
    ) {
        String expected = KnowledgeCatalogValidator.normalize(reason);
        for (OpportunityKnowledgeEnrichment.Action action : actions) {
            if (KnowledgeCatalogValidator.normalize(action.reason()).contains(expected)) {
                return action;
            }
        }
        return null;
    }

    private static OpportunityKnowledgeEnrichment.Action firstActionWithoutTechnology(
            List<OpportunityKnowledgeEnrichment.Action> actions
    ) {
        for (OpportunityKnowledgeEnrichment.Action action : actions) {
            if (action.technologyId() == null || action.technologyId().isBlank()) {
                return action;
            }
        }
        return null;
    }

    private static String actionIntentionKey(OpportunityKnowledgeEnrichment.Action action) {
        String reason = KnowledgeCatalogValidator.normalize(action.reason());
        String text = KnowledgeCatalogValidator.normalize(action.text());
        if (reason.contains("transferencia")) {
            return "transition";
        }
        if (reason.contains("evidencia") || text.contains("evidencia reproducible")
                || text.contains("uso verificable")) {
            return "visible-evidence";
        }
        if (reason.contains("brecha critica")) {
            return "critical-gap";
        }
        if (reason.contains("brecha secundaria")) {
            return "secondary-gap";
        }
        return text;
    }

    private static List<OpportunityKnowledgeDetailView.SharedSignalItem> sharedSignals(
            List<String> matchedSkills,
            ProfileEvidenceSummary profileEvidence
    ) {
        if (profileEvidence == null) {
            return List.of();
        }
        List<OpportunityKnowledgeDetailView.SharedSignalItem> out = new ArrayList<>();
        for (String skill : safe(matchedSkills)) {
            profileEvidence.strongestEvidenceFor(skill)
                    .filter(evidence -> evidence.evidenceType() != ProfessionalEvidenceType.MISSING)
                    .ifPresent(evidence -> out.add(new OpportunityKnowledgeDetailView.SharedSignalItem(
                            skill,
                            evidenceLabel(evidence.evidenceType()),
                            evidence.evidenceType().name(),
                            OUT_OF_SCOPE_SHARED_SIGNAL_WARNING
                    )));
            if (out.size() == 2) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static OpportunityKnowledgeDetailView.StrengthItem toStrength(
            OpportunityKnowledgeEnrichment.Strength strength
    ) {
        ProfessionalEvidenceType type = strength.evidenceType();
        return new OpportunityKnowledgeDetailView.StrengthItem(
                strength.technologyLabel(),
                evidenceLabel(type),
                type == null ? "UNKNOWN" : type.name(),
                strength.explanation(),
                evidenceLimit(type)
        );
    }

    private static String evidenceLabel(ProfessionalEvidenceType type) {
        if (type == null) return "Sin evidencia clasificada";
        return switch (type) {
            case WORK_EXPERIENCE -> "Laboral";
            case PROJECT -> "Proyecto";
            case ACADEMIC -> "Academica";
            case DECLARED_ONLY -> "Declarada";
            case TRANSFERABLE -> "Transferible";
            case MISSING -> "Sin evidencia";
        };
    }

    private static String evidenceLimit(ProfessionalEvidenceType type) {
        if (type == null || type == ProfessionalEvidenceType.MISSING) {
            return "La coincidencia todavia no tiene evidencia clasificada en el perfil.";
        }
        return switch (type) {
            case WORK_EXPERIENCE -> null;
            case PROJECT -> "Suma como practica visible, no como experiencia laboral.";
            case ACADEMIC -> "Suma como practica academica, no como experiencia laboral.";
            case DECLARED_ONLY -> "Esta declarada, pero todavia requiere evidencia visible de uso.";
            case TRANSFERABLE -> "Es una senal relacionada; no se presenta como skill demostrada.";
            case MISSING -> null;
        };
    }

    private static String transferNote(
            OpportunityKnowledgeEnrichment.Gap gap,
            List<OpportunityKnowledgeEnrichment.Transfer> transfers,
            int gapIndex
    ) {
        String technology = KnowledgeCatalogValidator.normalize(gap.technologyLabel());
        for (OpportunityKnowledgeEnrichment.Transfer transfer : transfers) {
            if (KnowledgeCatalogValidator.normalize(transfer.warning()).contains(technology)) {
                return transfer.warning();
            }
        }
        return gapIndex == 0 && transfers.size() == 1 ? transfers.get(0).warning() : null;
    }

    private static String summary(
            OpportunityKnowledgeEnrichment.CoverageLevel coverage,
            String roleFamilyLabel
    ) {
        return switch (coverage) {
            case DIRECT_COVERAGE -> "La oportunidad comparte un nucleo "
                    + (roleFamilyLabel == null ? "profesional" : roleFamilyLabel)
                    + " con evidencia visible en tu perfil.";
            case PARTIAL_COVERAGE -> "Hay senales aprovechables para esta transicion, aunque faltan evidencias especificas del dominio.";
            case LOW_CONTEXT -> "La oferta tiene poco detalle verificable; se evita inferir requisitos adicionales.";
            case OUT_OF_SCOPE -> "Existe cercania semantica, pero no hay evidencia suficiente para presentarla como afinidad profesional directa.";
        };
    }

    private static String coverageLabel(OpportunityKnowledgeEnrichment.CoverageLevel coverage) {
        return switch (coverage) {
            case DIRECT_COVERAGE -> "Cobertura directa";
            case PARTIAL_COVERAGE -> "Cobertura parcial";
            case LOW_CONTEXT -> "Contexto limitado";
            case OUT_OF_SCOPE -> "Fuera de alcance";
        };
    }

    private static String roleLabel(String roleId) {
        return switch (code(roleId)) {
            case "BACKEND" -> "Backend";
            case "DATA" -> "Data / BI";
            case "IT_SUPPORT" -> "IT Support";
            case "CLOUD_DEVOPS" -> "Cloud / DevOps";
            case "APP_SUPPORT_OPERATIONS" -> "Application Support / Operations";
            case "SECURITY_IAM" -> "Security / IAM";
            case "WEB_FULL_STACK" -> "Web Full Stack";
            case "WEB_FRONTEND" -> "Web Frontend";
            case "DATA_ENGINEERING" -> "Data Engineering";
            case "QA_AUTOMATION" -> "QA Automation";
            case "INFRASTRUCTURE_NETWORKS" -> "Infrastructure / Networks";
            case "DATABASE_ENGINEERING" -> "Database Engineering";
            case "SECURITY_ENGINEERING" -> "Security Engineering";
            case "AI_ML_APPLIED" -> "Applied AI / ML";
            default -> roleId;
        };
    }

    private static String routeLabel(String fromRoleId, String toRoleId) {
        return roleLabel(fromRoleId) + " → " + roleLabel(toRoleId);
    }

    private static boolean containsPhrase(String normalizedText, String phrase) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + KnowledgeCatalogValidator.normalize(phrase) + " ");
    }

    private static boolean containsAnyPhrase(String normalizedText, String... phrases) {
        for (String phrase : phrases) {
            if (containsPhrase(normalizedText, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static int countPhrases(String normalizedText, String... phrases) {
        int count = 0;
        for (String phrase : phrases) {
            if (containsPhrase(normalizedText, phrase)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsCloudDevopsTitle(String title) {
        return containsAnyPhrase(
                title,
                "cloud devops", "devops engineer", "cloud engineer", "sre", "site reliability engineer"
        );
    }

    private static boolean containsCloudDevopsRole(String text) {
        return containsAnyPhrase(
                text,
                "cloud devops", "devops engineer", "cloud engineer", "site reliability engineering",
                "infrastructure as code", "platform engineering"
        );
    }

    private static boolean containsBackendTitle(String title) {
        return containsAnyPhrase(
                title,
                "backend", "back end", "java developer", "java backend", "backend developer"
        );
    }

    private static boolean containsBackendRole(String text) {
        return containsAnyPhrase(
                text,
                "backend developer", "backend engineer", "backend services", "microservices",
                "java developer", "spring boot"
        );
    }

    private static boolean containsItSupportTitle(String title) {
        return containsAnyPhrase(
                title,
                "technical support", "it support", "help desk", "service desk", "desktop support"
        );
    }

    private static boolean containsItSupportRole(String text) {
        return containsAnyPhrase(
                text,
                "technical support", "it support", "help desk", "service desk", "desktop support",
                "soporte tecnico"
        );
    }

    private static boolean containsAppSupportTitle(String title) {
        return containsAnyPhrase(
                title,
                "application support", "app support", "production support", "tier 3 support", "support analyst",
                "soporte a aplicaciones", "soporte de aplicaciones", "soporte aplicativo", "soporte productivo",
                "analista de aplicaciones", "operaciones de aplicaciones"
        );
    }

    private static boolean containsAppSupportRole(String text) {
        return containsAnyPhrase(
                text,
                "application support", "app support", "production support", "tier 3 support",
                "application operations", "soporte a aplicaciones", "soporte de aplicaciones",
                "soporte aplicativo", "soporte productivo", "analista de aplicaciones",
                "operaciones de aplicaciones"
        );
    }

    private static boolean containsFullStackTitle(String title) {
        return containsAnyPhrase(title, "full stack", "fullstack");
    }

    private static boolean containsFullStackRole(String text) {
        return containsAnyPhrase(text, "full stack", "fullstack")
                || (containsAnyPhrase(text, "frontend", "react", "angular", "vue")
                && containsAnyPhrase(text, "backend", "api", "spring boot", "node js"));
    }

    private static boolean containsFrontendTitle(String title) {
        return containsAnyPhrase(title, "frontend", "front end", "ui developer");
    }

    private static boolean containsFrontendRole(String text) {
        return containsAnyPhrase(text, "frontend developer", "front end developer", "ui developer");
    }

    private static boolean containsSecurityRole(String text) {
        return containsAnyPhrase(
                text,
                "security", "appsec", "application security", "platform security", "secure sdlc",
                "owasp", "sast", "dast", "vulnerability", "iam", "identity management",
                "identity and access management", "oauth", "oidc", "saml", "okta", "cyberark", "sailpoint"
        );
    }

    private static String securityRole(String text) {
        if (containsAnyPhrase(
                text,
                "iam", "identity management", "identity and access management", "okta",
                "cyberark", "sailpoint", "saml", "oidc"
        )) {
            return "SECURITY_IAM";
        }
        return "SECURITY_ENGINEERING";
    }

    private static String code(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String opportunityText(Job job) {
        if (job == null) {
            return "";
        }
        return KnowledgeCatalogValidator.normalize(String.join(
                " ",
                safe(job.getTitle()),
                safe(job.getDescription()),
                safe(job.getRequirementsText())
        ));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record ActionDraft(
            String technologyId,
            String intentionKey,
            OpportunityKnowledgeDetailView.ActionItem item
    ) {
    }

    record OpportunityRoleResolution(String primaryRole, String secondaryRole) {
    }
}
