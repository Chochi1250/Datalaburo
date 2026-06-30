package com.DataLaburo.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TechnologyVisualCatalogTest {

    @Test
    void discoversBundledTechnologyImagesFromTheClasspath() {
        TechnologyVisualCatalog catalog = new TechnologyVisualCatalog();

        assertTrue(catalog.resolve("Java").hasImage());
        assertTrue(catalog.resolve("Spring Boot").hasImage());
        assertTrue(catalog.resolve("PostgreSQL").hasImage());
        assertTrue(catalog.resolve("Docker").hasImage());
    }

    @Test
    void resolvesKnownImagesAliasesAndExplicitColors() throws IOException {
        TechnologyVisualCatalog catalog = catalogWithImages(
                "java.png",
                "spring-icon.svg",
                "postgresql.png",
                "docker.png"
        );

        assertVisual(catalog.resolve("Java"), "/images/tech/java.png", "#F97316", true);
        assertVisual(catalog.resolve("  SPRING_boot  "), "/images/tech/spring-icon.svg", "#22C55E", true);
        assertVisual(catalog.resolve("postgres"), "/images/tech/postgresql.png", "#0EA5E9", true);
        assertVisual(catalog.resolve("Docker"), "/images/tech/docker.png", "#2496ED", true);
        assertEquals("#2563EB", catalog.resolve("SQL").accentColor());
        assertEquals(catalog.resolve("SQL").accentColor(), catalog.resolve("sql").accentColor());
    }

    @Test
    void keepsTextFallbackForKnownOrUnknownTechnologyWithoutImage() throws IOException {
        TechnologyVisualCatalog catalog = catalogWithImages("java.png");

        TechnologyVisual restApis = catalog.resolve("REST APIs");
        TechnologyVisual unknown = catalog.resolve("Observability Toolkit");
        TechnologyVisual unknownVariant = catalog.resolve("observability-toolkit");

        assertFalse(restApis.hasImage());
        assertEquals("#7C3AED", restApis.accentColor());
        assertFalse(unknown.hasImage());
        assertEquals(unknown.accentColor(), unknownVariant.accentColor());
        assertNotEquals("#334155", unknown.accentColor());
    }

    @Test
    void detectsNewImageWhenCatalogIsCreatedAgainAfterRestart() throws IOException {
        TechnologyVisualCatalog beforeRestart = catalogWithImages("java.png");
        TechnologyVisualCatalog afterRestart = catalogWithImages("java.png", "observability-toolkit.svg");

        assertFalse(beforeRestart.resolve("Observability Toolkit").hasImage());
        assertVisual(
                afterRestart.resolve("observability toolkit"),
                "/images/tech/observability-toolkit.svg",
                afterRestart.resolve("observability toolkit").accentColor(),
                true
        );
    }

    @Test
    void normalizationIgnoresCaseSeparatorsAndAccentsWithoutFuzzyMatching() throws IOException {
        TechnologyVisualCatalog catalog = catalogWithImages("Python.png", "C++.png");

        assertTrue(catalog.resolve("Pythón").hasImage());
        assertTrue(catalog.resolve("C++").hasImage());
        assertFalse(catalog.resolve("Pythonista").hasImage());
    }

    private static TechnologyVisualCatalog catalogWithImages(String... fileNames) throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource[] resources = new Resource[fileNames.length];
        for (int index = 0; index < fileNames.length; index++) {
            Resource resource = mock(Resource.class);
            when(resource.getFilename()).thenReturn(fileNames[index]);
            resources[index] = resource;
        }
        when(resolver.getResources(anyString())).thenReturn(resources);
        return new TechnologyVisualCatalog(resolver);
    }

    private static void assertVisual(
            TechnologyVisual visual,
            String imagePath,
            String accentColor,
            boolean hasImage
    ) {
        assertEquals(imagePath, visual.imagePath());
        assertEquals(accentColor, visual.accentColor());
        assertEquals(hasImage, visual.hasImage());
    }
}
