package com.DataLaburo.web.service;

import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class JobClassificationService {
    public static final String CLASSIFICATION_VERSION = "JOB_CLASSIFICATION_V1";

    private final Clock clock;

    public JobClassificationService(Clock clock) {
        this.clock = clock;
    }

    public Instant classifiedAtNow() {
        return Instant.now(clock);
    }

    public JobClassification classify(Job job) {
        if (job == null) {
            return unknown();
        }
        return classify(job.getTitle(), job.getDescription(), firstNonBlank(job.getLocation(), job.getLocationRaw()));
    }

    public JobClassification classify(String title) {
        return classify(title, null, null);
    }

    public JobClassification classify(String title, String description) {
        return classify(title, description, null);
    }

    public JobClassification classify(String title, String description, String location) {
        String normalizedTitle = normalize(title);
        if (normalizedTitle.isBlank()) {
            return unknown();
        }

        return new JobClassification(
                classifyFamilyFromTitle(normalizedTitle),
                null,
                detectSeniority(normalizedTitle),
                detectWorkModality(location, description),
                detectEmploymentType(normalizedTitle, description)
        );
    }

    private static JobRoleFamily classifyFamilyFromTitle(String title) {
        if (containsAny(title, "full stack", "fullstack")) {
            return JobRoleFamily.FULL_STACK;
        }
        if (containsAny(title, "frontend", "front end", "react developer", "angular developer", "vue developer")) {
            return JobRoleFamily.FRONTEND;
        }
        if (containsAny(title, "ux designer", "ui designer", "ux ui", "ui ux", "product designer", "ux researcher", "user experience designer", "user interface designer")) {
            return JobRoleFamily.UX_UI_DESIGN;
        }
        if (containsAny(title, "network engineer", "noc engineer", "network operations", "network operations center", "telecom", "telecommunications", "telecom engineer", "telecom operations", "voip engineer")) {
            return JobRoleFamily.NETWORKING_TELECOM;
        }
        if (containsAny(title, "technical project manager", "technology project manager", "it project manager", "technical program manager", "technology program manager", "scrum master", "delivery manager", "technical pmo", "it pmo", "pmo tecnico", "technology pmo")) {
            return JobRoleFamily.PROJECT_PROGRAM_DELIVERY;
        }
        if (containsAny(title, "embedded software engineer", "embedded engineer", "firmware engineer", "firmware developer", "iot engineer", "iot developer")) {
            return JobRoleFamily.EMBEDDED_IOT;
        }
        if (containsAny(title, "unity developer", "unreal engine developer", "game developer", "gameplay engineer", "game engineer", "technical artist")) {
            return JobRoleFamily.GAME_DEVELOPMENT;
        }
        if (containsAny(title, "solidity developer", "blockchain engineer", "blockchain developer", "web3 developer", "smart contract developer")) {
            return JobRoleFamily.BLOCKCHAIN_WEB3;
        }
        if (containsAny(title, "solution architect", "solutions architect", "pre sales engineer", "presales engineer", "sales engineer", "solutions consultant", "technical consultant", "implementation consultant")) {
            return JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES;
        }
        if (containsAny(title, "technical lead", "tech lead", "lider tecnico", "engineering manager", "lead engineer", "principal engineer", "staff engineer", "software architect", "technical architect", "enterprise architect")) {
            return JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE;
        }
        if (containsAny(title, "ai engineer", "ai automation engineer", "ml engineer", "machine learning engineer", "genai engineer", "generative ai engineer", "llm engineer", "mlops engineer", "prompt engineer")) {
            return JobRoleFamily.AI_ML_AUTOMATION;
        }
        if (containsAny(title, "data engineer", "data analyst", "analytics engineer", "business intelligence", "bi analyst", "etl developer", "big data", "data scientist")) {
            return JobRoleFamily.DATA;
        }
        if (containsAny(title, "devops", "sre", "site reliability", "cloud engineer", "cloud infrastructure engineer", "platform engineer", "cloud devops")) {
            return JobRoleFamily.CLOUD_DEVOPS_SRE;
        }
        if (containsAny(title, "application support", "technical support", "support engineer", "support analyst", "it support", "help desk", "service desk", "soporte tecnico", "sysadmin", "systems administrator", "infrastructure analyst", "infraestructura soporte")) {
            return JobRoleFamily.INFRASTRUCTURE_SUPPORT;
        }
        if (containsAny(title, "security engineer", "security focused", "cybersecurity", "cyber security", "iam engineer", "iam", "soc analyst", "appsec", "devsecops")) {
            return JobRoleFamily.SECURITY;
        }
        if (containsAny(title, "dba", "database engineer", "database developer", "base de datos", "oracle dba", "data processing specialist")) {
            return JobRoleFamily.DATABASE;
        }
        if (isQaTitle(title)) {
            return JobRoleFamily.QA;
        }
        if (containsAny(title, "salesforce", "sap", "hyperion", "erp", "crm", "servicenow", "workday")) {
            return JobRoleFamily.ERP_CRM_ENTERPRISE;
        }
        if (containsAny(title, "business analyst", "product analyst", "product owner", "functional analyst", "analista funcional")) {
            return JobRoleFamily.PRODUCT_BUSINESS_ANALYSIS;
        }
        if (containsAny(title, "android developer", "android engineer", "ios developer", "ios engineer", "mobile developer", "mobile engineer", "flutter developer", "software engineer flutter", "react native developer")) {
            return JobRoleFamily.MOBILE;
        }
        if (containsAny(title, "backend", "back end", "java developer", "java engineer", "python developer", "python engineer", "node developer", "node js developer", "api developer", "dotnet developer", "net developer", "go developer")) {
            return JobRoleFamily.BACKEND;
        }
        if (isClearOutOfScope(title)) {
            return JobRoleFamily.OUT_OF_SCOPE;
        }
        if (isGenericSoftwareEngineeringTitle(title)) {
            return JobRoleFamily.SOFTWARE_ENGINEERING_GENERAL;
        }
        return JobRoleFamily.UNKNOWN;
    }

    private static String detectSeniority(String title) {
        if (containsAny(title, "trainee", "internship", "intern", "pasantia")) {
            return "TRAINEE";
        }
        if (containsAny(title, "junior", "jr")) {
            return "JUNIOR";
        }
        if (containsAny(title, "semi senior", "semisenior", "ssr", "intermediate", "mid level")) {
            return "MID";
        }
        if (containsAny(title, "senior", "sr")) {
            return "SENIOR";
        }
        if (containsAny(title, "technical lead", "tech lead", "lider tecnico", "lead engineer", "principal engineer", "staff engineer")) {
            return "LEAD";
        }
        if (containsAny(title, "engineering manager")) {
            return "MANAGER";
        }
        return null;
    }

    private static String detectWorkModality(String location, String description) {
        String locationText = normalize(location);
        if (containsAny(locationText, "remoto", "remote")) {
            return "REMOTE";
        }
        if (containsAny(locationText, "hibrido", "hybrid")) {
            return "HYBRID";
        }
        if (containsAny(locationText, "presencial", "onsite", "on site")) {
            return "ONSITE";
        }

        String descriptionText = normalize(description);
        boolean remote = containsAny(descriptionText, "remoto", "remote");
        boolean hybrid = containsAny(descriptionText, "hibrido", "hybrid");
        boolean onsite = containsAny(descriptionText, "presencial", "onsite", "on site", "oficina");
        int signals = (remote ? 1 : 0) + (hybrid ? 1 : 0) + (onsite ? 1 : 0);
        if (signals != 1) {
            return null;
        }
        if (remote) {
            return "REMOTE";
        }
        if (hybrid) {
            return "HYBRID";
        }
        return "ONSITE";
    }

    private static String detectEmploymentType(String title, String description) {
        String text = normalize((title == null ? "" : title) + " " + (description == null ? "" : description));
        if (containsAny(text, "full time", "fulltime", "jornada completa", "tiempo completo")) {
            return "FULLTIME";
        }
        if (containsAny(text, "part time", "parttime", "medio tiempo", "tiempo parcial")) {
            return "PARTTIME";
        }
        if (containsAny(text, "contrato", "contract", "contractor", "temporary", "temp")) {
            return "CONTRACT";
        }
        if (containsAny(text, "freelance", "autonomo", "independiente")) {
            return "FREELANCE";
        }
        return null;
    }

    private static boolean isQaTitle(String title) {
        if (containsAny(title, "ai chatbot tester", "chatbot tester")) {
            return false;
        }
        return containsAny(title, "qa engineer", "quality assurance", "test engineer", "software tester", "automation tester", "manual tester");
    }

    private static boolean isClearOutOfScope(String title) {
        return containsAny(title,
                "appointment setting specialist",
                "warehouse administrative",
                "administrative analyst",
                "vendedor",
                "sales representative",
                "account executive",
                "customer success",
                "recruiter",
                "hr analyst",
                "marketing specialist",
                "abogado",
                "lawyer"
        );
    }

    private static boolean isGenericSoftwareEngineeringTitle(String title) {
        if (containsAny(title, "business developer", "sales developer")) {
            return false;
        }
        return containsAny(title,
                "software engineer",
                "software developer",
                "application developer",
                "application engineer",
                "developer",
                "programmer"
        );
    }

    private static JobClassification of(JobRoleFamily roleFamily) {
        return new JobClassification(roleFamily, null, null, null, null);
    }

    private static JobClassification unknown() {
        return of(JobRoleFamily.UNKNOWN);
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (containsPhrase(text, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhrase(String text, String phrase) {
        String normalizedPhrase = normalize(phrase);
        if (normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + normalizedPhrase + " ");
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
