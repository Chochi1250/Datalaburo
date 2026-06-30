package com.DataLaburo.web.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TechnologyVisualCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnologyVisualCatalog.class);
    private static final String IMAGE_PATTERN = "classpath*:/static/images/tech/*";
    private static final String PUBLIC_IMAGE_ROOT = "/images/tech/";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "svg", "webp", "jpg", "jpeg", "gif");
    private static final List<String> FALLBACK_PALETTE = List.of(
            "#2563EB", "#0891B2", "#0D9488", "#16A34A",
            "#65A30D", "#CA8A04", "#EA580C", "#E11D48",
            "#DB2777", "#9333EA", "#7C3AED", "#4F46E5"
    );

    private static final Map<String, String> ALIASES = buildAliases();
    private static final Map<String, String> EXPLICIT_COLORS = Map.ofEntries(
            Map.entry("java", "#F97316"),
            Map.entry("spring", "#22C55E"),
            Map.entry("postgresql", "#0EA5E9"),
            Map.entry("sql", "#2563EB"),
            Map.entry("mysql", "#0891B2"),
            Map.entry("docker", "#2496ED"),
            Map.entry("restapis", "#7C3AED"),
            Map.entry("microservices", "#7C3AED"),
            Map.entry("javascript", "#EAB308"),
            Map.entry("typescript", "#2563EB"),
            Map.entry("python", "#3B82F6"),
            Map.entry("react", "#06B6D4"),
            Map.entry("nodejs", "#22C55E"),
            Map.entry("kubernetes", "#6366F1"),
            Map.entry("mongodb", "#10B981"),
            Map.entry("github", "#334155"),
            Map.entry("linux", "#475569"),
            Map.entry("oracle", "#DC2626"),
            Map.entry("html", "#F97316"),
            Map.entry("css", "#2563EB"),
            Map.entry("aws", "#F59E0B"),
            Map.entry("machinelearning", "#A855F7"),
            Map.entry("sap", "#2563EB"),
            Map.entry("ibm", "#2563EB"),
            Map.entry("c", "#00599C"),
            Map.entry("cplusplus", "#00599C"),
            Map.entry("csharp", "#7C3AED"),
            Map.entry("dotnet", "#512BD4")
    );

    private final ResourcePatternResolver resourceResolver;
    private Map<String, String> imagesByNormalizedName = Map.of();
    private Map<String, String> imagesByCanonicalName = Map.of();

    public TechnologyVisualCatalog() {
        this(new PathMatchingResourcePatternResolver());
    }

    TechnologyVisualCatalog(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        reload();
    }

    public TechnologyVisual resolve(String technologyName) {
        String requestedName = technologyName == null ? "" : technologyName.trim();
        String normalizedName = normalize(requestedName);
        String canonicalName = canonicalName(normalizedName);
        String imagePath = imagesByNormalizedName.get(normalizedName);
        if (imagePath == null) {
            imagePath = imagesByCanonicalName.get(canonicalName);
        }
        String accentColor = EXPLICIT_COLORS.getOrDefault(
                canonicalName,
                stableFallbackColor(canonicalName.isBlank() ? normalizedName : canonicalName)
        );

        return new TechnologyVisual(
                requestedName,
                normalizedName,
                imagePath,
                accentColor,
                imagePath != null
        );
    }

    void reload() {
        Map<String, String> normalizedImages = new LinkedHashMap<>();
        Map<String, String> canonicalImages = new LinkedHashMap<>();
        try {
            Resource[] resources = resourceResolver.getResources(IMAGE_PATTERN);
            Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(fileName -> fileName != null && isSupportedImage(fileName))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(fileName -> registerImage(fileName, normalizedImages, canonicalImages));
        } catch (IOException exception) {
            LOGGER.warn("Technology image catalog could not scan {}", IMAGE_PATTERN, exception);
        }
        imagesByNormalizedName = Map.copyOf(normalizedImages);
        imagesByCanonicalName = Map.copyOf(canonicalImages);
        LOGGER.info("Technology Visual Catalog loaded {} local images", imagesByNormalizedName.size());
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("+", "plus")
                .replace("#", "sharp");
        return normalized.replaceAll("[\\s._-]+", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private static void registerImage(
            String fileName,
            Map<String, String> normalizedImages,
            Map<String, String> canonicalImages
    ) {
        String stem = fileName.substring(0, fileName.lastIndexOf('.'));
        String normalizedStem = normalize(stem);
        if (normalizedStem.isBlank()) {
            return;
        }
        String publicPath = PUBLIC_IMAGE_ROOT + UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);
        normalizedImages.putIfAbsent(normalizedStem, publicPath);
        canonicalImages.putIfAbsent(canonicalName(normalizedStem), publicPath);
    }

    private static boolean isSupportedImage(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT));
    }

    private static String canonicalName(String normalizedName) {
        return ALIASES.getOrDefault(normalizedName, normalizedName);
    }

    private static String stableFallbackColor(String normalizedName) {
        String stableKey = normalizedName == null ? "" : normalizedName;
        return FALLBACK_PALETTE.get(Math.floorMod(stableKey.hashCode(), FALLBACK_PALETTE.size()));
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerAliases(aliases, "java", "java", "openjdk", "jdk");
        registerAliases(aliases, "spring", "spring", "spring boot", "spring-boot", "spring framework", "spring-icon");
        registerAliases(aliases, "postgresql", "postgresql", "postgres", "postgres sql");
        registerAliases(aliases, "sql", "sql", "structured query language");
        registerAliases(aliases, "mysql", "mysql", "my sql");
        registerAliases(aliases, "nodejs", "node", "node.js", "nodejs", "node js");
        registerAliases(aliases, "dotnet", ".net", "dotnet", "asp.net", "aspnet", "asp.net core", "aspnetcore");
        registerAliases(aliases, "restapis", "rest", "rest api", "rest apis", "api", "apis", "api rest", "apis rest");
        registerAliases(aliases, "microservices", "microservice", "microservices", "micro servicio", "micro servicios");
        registerAliases(aliases, "javascript", "javascript", "java script", "js", "ecmascript");
        registerAliases(aliases, "typescript", "typescript", "type script", "ts");
        registerAliases(aliases, "kubernetes", "kubernetes", "k8s");
        registerAliases(aliases, "mongodb", "mongodb", "mongo", "mongo db");
        registerAliases(aliases, "github", "github", "git hub", "git");
        registerAliases(aliases, "linux", "linux", "bash", "shell");
        registerAliases(aliases, "python", "python", "python3");
        registerAliases(aliases, "aws", "aws", "amazon web services");
        registerAliases(aliases, "machinelearning", "machine learning", "ml");
        registerAliases(aliases, "html", "html", "html5");
        registerAliases(aliases, "css", "css", "css3");
        registerAliases(aliases, "cplusplus", "c++", "cpp");
        registerAliases(aliases, "csharp", "c#", "c sharp");
        return Map.copyOf(aliases);
    }

    private static void registerAliases(Map<String, String> aliases, String canonicalName, String... values) {
        aliases.put(normalize(canonicalName), normalize(canonicalName));
        for (String value : values) {
            aliases.put(normalize(value), normalize(canonicalName));
        }
    }
}
