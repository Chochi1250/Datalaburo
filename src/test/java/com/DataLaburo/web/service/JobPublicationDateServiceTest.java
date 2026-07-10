package com.DataLaburo.web.service;

import com.DataLaburo.web.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPublicationDateServiceTest {
    private static final ZoneId UI_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-07-31T15:00:00Z");
    private final JobPublicationDateService service = new JobPublicationDateService(Clock.fixed(NOW, UI_ZONE));

    @Test
    void parsesSpanishRelativeTextIntoEstimatedInstants() {
        Instant observedAt = Instant.parse("2026-07-03T12:00:00Z");

        assertEquals(
                observedAt.minus(Duration.ofDays(6)),
                service.estimatePublishedAt(" Hace 6 días ", observedAt).orElseThrow()
        );
        assertEquals(
                observedAt.minus(Duration.ofDays(7)),
                service.estimatePublishedAt("Hace 1 semana", observedAt).orElseThrow()
        );
        assertEquals(
                observedAt.minus(Duration.ofDays(30)),
                service.estimatePublishedAt("hace 1 mes", observedAt).orElseThrow()
        );
    }

    @Test
    void parsesTodayYesterdayAndSimpleEnglishText() {
        Instant observedAt = Instant.parse("2026-07-03T12:00:00Z");

        assertEquals(observedAt, service.estimatePublishedAt("hoy", observedAt).orElseThrow());
        assertEquals(observedAt.minus(Duration.ofDays(1)), service.estimatePublishedAt("yesterday", observedAt).orElseThrow());
        assertEquals(observedAt.minus(Duration.ofDays(14)), service.estimatePublishedAt("2 weeks ago", observedAt).orElseThrow());
    }

    @Test
    void rejectsUnknownOrAmbiguousRawText() {
        Instant observedAt = Instant.parse("2026-07-03T12:00:00Z");

        assertTrue(service.estimatePublishedAt("publicada recientemente", observedAt).isEmpty());
        assertTrue(service.estimatePublishedAt("republicado hace 1 semana", observedAt).isEmpty());
        assertTrue(service.estimatePublishedAt(null, observedAt).isEmpty());
    }

    @Test
    void formatsDynamicLabelsWithSingleUnitTransitions() {
        assertEquals("Hace 6 días", service.formatPublishedAt(Instant.parse("2026-07-25T12:00:00Z")).orElseThrow());
        assertEquals("Hace 1 semana", service.formatPublishedAt(Instant.parse("2026-07-24T12:00:00Z")).orElseThrow());
        assertEquals("Hace 3 semanas", service.formatPublishedAt(Instant.parse("2026-07-04T12:00:00Z")).orElseThrow());
        assertEquals("Hace 1 mes", service.formatPublishedAt(Instant.parse("2026-07-03T12:00:00Z")).orElseThrow());
        assertEquals("Hace 2 meses", service.formatPublishedAt(Instant.parse("2026-06-01T12:00:00Z")).orElseThrow());
    }

    @Test
    void clampsFutureDatesToToday() {
        assertEquals("Hoy", service.formatPublishedAt(NOW.plus(Duration.ofDays(2))).orElseThrow());
    }

    @Test
    void fallsBackToRawTextWhenNoEstimateExists() {
        Job job = new Job();
        job.setPostedAtText("publicada recientemente");

        assertEquals("publicada recientemente", service.labelFor(job).orElseThrow());
    }
}
