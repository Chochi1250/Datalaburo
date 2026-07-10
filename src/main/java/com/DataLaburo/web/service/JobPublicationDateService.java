package com.DataLaburo.web.service;

import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobPublicationDateService {
    public static final ZoneId PRESENTATION_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Duration APPROXIMATE_MONTH = Duration.ofDays(30);
    private static final Pattern SPANISH_RELATIVE_TIME = Pattern.compile(
            "hace\\s+(\\d+)\\s+(minuto|minutos|hora|horas|dia|dias|semana|semanas|mes|meses)"
    );
    private static final Pattern ENGLISH_RELATIVE_TIME = Pattern.compile(
            "(\\d+)\\s+(minute|minutes|hour|hours|day|days|week|weeks|month|months)\\s+ago"
    );

    private final Clock clock;

    public JobPublicationDateService(Clock clock) {
        this.clock = clock;
    }

    public Instant observedAtNow() {
        return Instant.now(clock);
    }

    public Optional<Instant> estimatePublishedAt(String rawText, Instant observedAt) {
        if (observedAt == null) {
            return Optional.empty();
        }
        return parseRelativeDuration(rawText).map(observedAt::minus);
    }

    public Optional<Duration> parseRelativeDuration(String rawText) {
        String normalized = normalizeForRelativeTime(rawText);
        if (normalized.isBlank() || looksLikeRepostText(normalized)) {
            return Optional.empty();
        }
        if ("hoy".equals(normalized) || "today".equals(normalized)) {
            return Optional.of(Duration.ZERO);
        }
        if ("ayer".equals(normalized) || "yesterday".equals(normalized)) {
            return Optional.of(Duration.ofDays(1));
        }

        Matcher spanish = SPANISH_RELATIVE_TIME.matcher(normalized);
        if (spanish.find()) {
            return Optional.of(relativeDuration(Integer.parseInt(spanish.group(1)), spanish.group(2)));
        }

        Matcher english = ENGLISH_RELATIVE_TIME.matcher(normalized);
        if (english.find()) {
            return Optional.of(relativeDuration(Integer.parseInt(english.group(1)), english.group(2)));
        }

        return Optional.empty();
    }

    public Optional<Instant> effectivePublishedAt(Job job) {
        if (job == null) {
            return Optional.empty();
        }
        if (job.getPublishedAtEstimated() != null) {
            return Optional.of(job.getPublishedAtEstimated());
        }
        return estimatePublishedAt(job.getPostedAtText(), observedAtNow());
    }

    public Optional<String> labelFor(Job job) {
        if (job == null) {
            return Optional.empty();
        }
        Optional<String> dynamicLabel = formatPublishedAt(job.getPublishedAtEstimated());
        if (dynamicLabel.isPresent()) {
            return dynamicLabel;
        }
        return firstNonBlank(job.getPostedAtText());
    }

    public Optional<String> formatPublishedAt(Instant publishedAt) {
        if (publishedAt == null) {
            return Optional.empty();
        }
        LocalDate today = observedAtNow().atZone(PRESENTATION_ZONE).toLocalDate();
        LocalDate publishedDate = publishedAt.atZone(PRESENTATION_ZONE).toLocalDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(publishedDate, today));

        if (days == 0) {
            return Optional.of("Hoy");
        }
        if (days <= 6) {
            return Optional.of("Hace " + days + " " + (days == 1 ? "día" : "días"));
        }
        if (days <= 13) {
            return Optional.of("Hace 1 semana");
        }
        if (days <= 20) {
            return Optional.of("Hace 2 semanas");
        }
        if (days <= 27) {
            return Optional.of("Hace 3 semanas");
        }
        if (days <= 59) {
            return Optional.of("Hace 1 mes");
        }
        long months = Math.max(2, days / 30);
        return Optional.of("Hace " + months + " meses");
    }

    private static Duration relativeDuration(int count, String unit) {
        if (unit.startsWith("minuto") || unit.startsWith("minute")) {
            return Duration.ofMinutes(count);
        }
        if (unit.startsWith("hora") || unit.startsWith("hour")) {
            return Duration.ofHours(count);
        }
        if (unit.startsWith("dia") || unit.startsWith("day")) {
            return Duration.ofDays(count);
        }
        if (unit.startsWith("semana") || unit.startsWith("week")) {
            return Duration.ofDays(count * 7L);
        }
        if (unit.startsWith("mes") || unit.startsWith("month")) {
            return APPROXIMATE_MONTH.multipliedBy(count);
        }
        return Duration.ZERO;
    }

    private static boolean looksLikeRepostText(String normalized) {
        return normalized.contains("republicad")
                || normalized.contains("reposte")
                || normalized.contains("reposted")
                || normalized.contains("republished");
    }

    private static Optional<String> firstNonBlank(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }

    private static String normalizeForRelativeTime(String text) {
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
}
