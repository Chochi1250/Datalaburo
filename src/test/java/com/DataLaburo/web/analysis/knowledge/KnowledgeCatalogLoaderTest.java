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
    void loadsVersionedFoundationCatalog() {
        KnowledgeCatalog catalog = loader.load(new ClassPathResource(KnowledgeCatalogLoader.CATALOG_PATH));

        assertEquals(1, catalog.version());
        assertEquals(List.of("BACKEND", "DATA", "IT_SUPPORT"), catalog.roleFamilies().stream()
                .map(KnowledgeCatalog.RoleFamilyDefinition::id)
                .toList());
        assertEquals(List.of(
                "JAVA", "SPRING_BOOT", "REST_APIS", "SQL_POSTGRESQL",
                "PYTHON", "POWER_BI", "DOCKER", "GIT"
        ), catalog.technologies().stream()
                .map(KnowledgeCatalog.TechnologyDefinition::id)
                .toList());
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
                backend.alignedCopy(),
                backend.transitionCopy()
        ));
        KnowledgeCatalog invalid = new KnowledgeCatalog(
                valid.version(),
                roles,
                valid.technologies(),
                valid.transfers(),
                valid.seniorityRules(),
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
                backend.alignedCopy(),
                backend.transitionCopy()
        ));

        KnowledgeCatalogException exception = assertThrows(
                KnowledgeCatalogException.class,
                () -> validator.validate(new KnowledgeCatalog(
                        valid.version(),
                        roles,
                        valid.technologies(),
                        valid.transfers(),
                        valid.seniorityRules(),
                        valid.fallbacks()
                ))
        );

        assertTrue(exception.getMessage().contains("must match [A-Z][A-Z0-9_]*"));
    }
}
