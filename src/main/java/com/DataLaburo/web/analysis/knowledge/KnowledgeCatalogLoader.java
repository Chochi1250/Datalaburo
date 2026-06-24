package com.DataLaburo.web.analysis.knowledge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KnowledgeCatalogLoader {
    public static final String CATALOG_PATH = "knowledge/knowledge-catalog-v1.yaml";

    private final KnowledgeCatalogValidator validator;
    private final ObjectMapper yamlMapper;
    private KnowledgeCatalog catalog;

    public KnowledgeCatalogLoader(KnowledgeCatalogValidator validator) {
        this.validator = validator;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    @PostConstruct
    void initialize() {
        catalog = load(new ClassPathResource(CATALOG_PATH));
    }

    public KnowledgeCatalog catalog() {
        if (catalog == null) {
            throw new KnowledgeCatalogException("Knowledge Catalog was requested before initialization");
        }
        return catalog;
    }

    public KnowledgeCatalog load(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new KnowledgeCatalogException("Knowledge Catalog resource does not exist: " + description(resource));
        }
        try (var input = resource.getInputStream()) {
            KnowledgeCatalog loaded = yamlMapper.readValue(input, KnowledgeCatalog.class);
            validator.validate(loaded);
            return loaded;
        } catch (KnowledgeCatalogException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new KnowledgeCatalogException(
                    "Invalid Knowledge Catalog YAML at " + description(resource) + ": " + rootMessage(exception),
                    exception
            );
        }
    }

    private static String description(Resource resource) {
        return resource == null ? "<null>" : resource.getDescription();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
