package com.DataLaburo.web.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferabilityServiceTest {
    private final TransferabilityService service = new TransferabilityService();

    @Test
    void detectsExpectedContainerTransfer() {
        List<TransferableSkill> transferable = service.findTransferableSkills(
                List.of("Docker", "Java"),
                List.of("Kubernetes")
        );

        assertEquals(1, transferable.size());
        assertEquals("Docker", transferable.get(0).from());
        assertEquals("Kubernetes", transferable.get(0).to());
        assertEquals(TransferStrength.PARTIAL, transferable.get(0).strength());
    }

    @Test
    void detectsExpectedSqlTransfer() {
        List<TransferableSkill> transferable = service.findTransferableSkills(
                List.of("SQL"),
                List.of("PostgreSQL", "MySQL")
        );

        assertEquals(2, transferable.size());
        assertTrue(transferable.stream().anyMatch(t -> t.to().equals("PostgreSQL")));
        assertTrue(transferable.stream().anyMatch(t -> t.to().equals("MySQL")));
    }

    @Test
    void deduplicatesBackendAliasesToSingleConceptualTransfer() {
        List<TransferableSkill> transferable = service.findTransferableSkills(
                List.of("Backend", "Backend Development", "Backend Developer"),
                List.of("Cloud")
        );

        assertEquals(1, transferable.size());
        assertEquals("Backend", transferable.get(0).from());
        assertEquals("Cloud", transferable.get(0).to());
    }
}
