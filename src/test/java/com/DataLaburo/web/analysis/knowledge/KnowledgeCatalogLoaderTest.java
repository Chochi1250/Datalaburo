package com.DataLaburo.web.analysis.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeCatalogLoaderTest {
    private final KnowledgeCatalogValidator validator = new KnowledgeCatalogValidator();
    private final KnowledgeCatalogLoader loader = new KnowledgeCatalogLoader(validator);

    @Test
    void loadsVersionedExpandedCatalog() {
        KnowledgeCatalog catalog = loader.load(new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH));

        assertEquals(1, catalog.version());
        assertEquals(List.of(
                "BACKEND", "DATA", "IT_SUPPORT", "CLOUD_DEVOPS",
                "APP_SUPPORT_OPERATIONS", "SECURITY_IAM", "WEB_FULL_STACK",
                "WEB_FRONTEND", "DATA_ENGINEERING", "QA_AUTOMATION",
                "INFRASTRUCTURE_NETWORKS", "DATABASE_ENGINEERING",
                "SECURITY_ENGINEERING", "AI_ML_APPLIED"
        ), catalog.roleFamilies().stream()
                .map(KnowledgeCatalog.RoleFamilyDefinition::id)
                .toList());
        List<String> technologyIds = catalog.technologies().stream()
                .map(KnowledgeCatalog.TechnologyDefinition::id)
                .toList();
        assertEquals(171, technologyIds.size());
        assertTrue(technologyIds.containsAll(List.of(
                "JAVA", "SPRING_BOOT", "REST_APIS", "SQL_POSTGRESQL",
                "PYTHON", "POWER_BI", "DOCKER", "GIT",
                "MICROSOFT_FABRIC", "SQL_SERVER", "T_SQL", "SSIS", "ETL_ELT",
                "APACHE_SPARK", "DATABRICKS", "SNOWFLAKE", "BIGQUERY",
                "WINDOWS_SERVER", "ACTIVE_DIRECTORY", "MICROSOFT_365", "DNS", "DHCP", "NETWORKING",
                "AWS", "AZURE", "GCP", "KUBERNETES", "OPENSHIFT", "TERRAFORM", "LINUX",
                "CI_CD", "JENKINS", "GITHUB_ACTIONS",
                "SERVICENOW", "JIRA", "POSTMAN", "GRAFANA", "PROMETHEUS", "DATADOG",
                "KIBANA", "SENTRY", "BASH",
                "IAM", "ENTRA_ID", "OAUTH_2", "OIDC", "SAML", "OKTA", "CYBERARK",
                "SAILPOINT", "SIEM", "SOAR",
                "JAVASCRIPT", "TYPESCRIPT", "HTML", "CSS", "REACT", "VUE", "ANGULAR", "NODE_JS"
        )));
        assertTrue(technologyIds.containsAll(List.of(
                "NEXT_JS", "VITE", "JEST", "REACT_TESTING_LIBRARY", "CYPRESS", "PLAYWRIGHT",
                "AIRFLOW", "DBT", "KAFKA", "REDSHIFT", "DATAFLOW", "PANDAS", "DATA_QUALITY",
                "QA_TESTING", "MANUAL_TESTING", "AUTOMATED_TESTING", "SELENIUM", "JUNIT", "TESTNG",
                "API_TESTING", "PERFORMANCE_TESTING", "TEST_CASES", "BUG_REPORTING",
                "TCP_IP", "ROUTING", "SWITCHING", "FIREWALLS", "VPN", "VMWARE", "HYPER_V",
                "STORAGE", "BACKUPS", "MYSQL", "ORACLE", "MONGODB", "INDEXING", "QUERY_TUNING",
                "STORED_PROCEDURES", "REPLICATION", "HIGH_AVAILABILITY", "DATABASE_PERFORMANCE",
                "OWASP", "APPSEC", "SAST", "DAST", "DEPENDENCY_SCANNING", "VULNERABILITY_MANAGEMENT",
                "SECURE_SDLC", "EDR", "CLOUD_SECURITY", "SECRETS_MANAGEMENT", "THREAT_MODELING",
                "PANDAS", "NUMPY", "SCIKIT_LEARN", "TENSORFLOW", "PYTORCH", "JUPYTER", "LLM",
                "RAG", "EMBEDDINGS", "VECTOR_DATABASES", "MODEL_EVALUATION", "PROMPT_ENGINEERING",
                "MLFLOW", "MODEL_MONITORING", "MLOPS"
        )));
        assertTrue(catalog.explicitOutOfScopeRoleAliases().containsAll(List.of(
                "mobile", "erp", "embedded", "game development", "ux", "customer success", "sales"
        )));
    }

    @Test
    void invalidYamlFailsWithResourceAndRootCause() {
        ByteArrayResource invalid = new ByteArrayResource("version: [".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "invalid-inline-catalog.yaml";
            }
        };

        KnowledgeCatalogException exception = assertThrows(
                KnowledgeCatalogException.class,
                () -> loader.load(invalid)
        );

        assertTrue(exception.getMessage().contains("Invalid Knowledge Catalog YAML"));
        assertTrue(exception.getMessage().contains("invalid-inline-catalog.yaml"));
    }

    @Test
    void missingTechnologyReferenceFailsClearly() {
        KnowledgeCatalog valid = loader.load(new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH));
        List<KnowledgeCatalog.RoleFamilyDefinition> roles = new ArrayList<>(valid.roleFamilies());
        KnowledgeCatalog.RoleFamilyDefinition backend = roles.get(0);
        roles.set(0, new KnowledgeCatalog.RoleFamilyDefinition(
                backend.id(),
                backend.label(),
                backend.aliases(),
                backend.evidenceDomains(),
                List.of("DOES_NOT_EXIST"),
                backend.seniorityEvidenceTechnologyRefs(),
                backend.alignedCopy(),
                backend.transitionCopy(),
                backend.limitedContextCopy(),
                backend.favorableSignals(),
                backend.strongEvidenceSignals(),
                backend.supportingEvidenceSignals(),
                backend.insufficientEvidenceSignals(),
                backend.frequentGapExplanations(),
                backend.concreteActions(),
                backend.projectEvidenceIdeas(),
                backend.cvIdeas(),
                backend.shortRoadmap()
        ));
        KnowledgeCatalog invalid = new KnowledgeCatalog(
                valid.version(),
                roles,
                valid.technologies(),
                valid.transfers(),
                valid.seniorityRules(),
                valid.explicitOutOfScopeRoleAliases(),
                valid.fallbacks()
        );

        KnowledgeCatalogException exception = assertThrows(
                KnowledgeCatalogException.class,
                () -> validator.validate(invalid)
        );

        assertTrue(exception.getMessage().contains("references missing id DOES_NOT_EXIST"));
    }

    @Test
    void invalidIdFailsClearly() {
        KnowledgeCatalog valid = loader.load(new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH));
        List<KnowledgeCatalog.RoleFamilyDefinition> roles = new ArrayList<>(valid.roleFamilies());
        KnowledgeCatalog.RoleFamilyDefinition backend = roles.get(0);
        roles.set(0, new KnowledgeCatalog.RoleFamilyDefinition(
                "backend-invalid",
                backend.label(),
                backend.aliases(),
                backend.evidenceDomains(),
                backend.coreTechnologyRefs(),
                backend.seniorityEvidenceTechnologyRefs(),
                backend.alignedCopy(),
                backend.transitionCopy(),
                backend.limitedContextCopy(),
                backend.favorableSignals(),
                backend.strongEvidenceSignals(),
                backend.supportingEvidenceSignals(),
                backend.insufficientEvidenceSignals(),
                backend.frequentGapExplanations(),
                backend.concreteActions(),
                backend.projectEvidenceIdeas(),
                backend.cvIdeas(),
                backend.shortRoadmap()
        ));

        KnowledgeCatalogException exception = assertThrows(
                KnowledgeCatalogException.class,
                () -> validator.validate(new KnowledgeCatalog(
                        valid.version(),
                        roles,
                        valid.technologies(),
                        valid.transfers(),
                        valid.seniorityRules(),
                        valid.explicitOutOfScopeRoleAliases(),
                        valid.fallbacks()
                ))
        );

        assertTrue(exception.getMessage().contains("must match [A-Z][A-Z0-9_]*"));
    }
}
