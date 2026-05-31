package com.DataLaburo.web.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEquivalenceServiceTest {
    private final SkillEquivalenceService service = new SkillEquivalenceService();

    @Test
    void detectsDatabaseEngineAsPartialSqlCoreEquivalence() {
        List<SkillEquivalenceSignal> signals = service.findSignals(
                List.of("PostgreSQL"),
                List.of("SQL")
        );

        assertEquals(1, signals.size());
        assertEquals("PostgreSQL", signals.get(0).candidateSkill());
        assertEquals("SQL", signals.get(0).targetSkill());
        assertEquals("PARTIAL_EQUIVALENCE", signals.get(0).relation());
    }

    @Test
    void detectsCloudProviderAsPartialCloudTransfer() {
        List<SkillEquivalenceSignal> signals = service.findSignals(
                List.of("AWS"),
                List.of("Cloud")
        );

        assertEquals(1, signals.size());
        assertEquals("AWS", signals.get(0).candidateSkill());
        assertEquals("Cloud", signals.get(0).targetSkill());
        assertEquals("PARTIAL_TRANSFER", signals.get(0).relation());
    }

    @Test
    void detectsItilAndItsmAsRelatedSkills() {
        List<SkillEquivalenceSignal> signals = service.findSignals(
                List.of("ITIL"),
                List.of("ITSM")
        );

        assertEquals(1, signals.size());
        assertEquals("RELATED", signals.get(0).relation());
    }

    @Test
    void detectsSpringBootContextualRelationships() {
        List<SkillEquivalenceSignal> signals = service.findSignals(
                List.of("Spring Boot"),
                List.of("REST API", "Microservices")
        );

        assertEquals(2, signals.size());
        assertTrue(signals.stream().anyMatch(signal -> "REST API".equals(signal.targetSkill())
                && "CONTEXTUAL".equals(signal.relation())));
        assertTrue(signals.stream().anyMatch(signal -> "Microservices".equals(signal.targetSkill())
                && "CONTEXTUAL".equals(signal.relation())));
    }
}
