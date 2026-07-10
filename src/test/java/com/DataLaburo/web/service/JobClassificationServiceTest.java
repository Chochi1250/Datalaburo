package com.DataLaburo.web.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobClassificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-03T12:00:00Z");
    private final JobClassificationService service = new JobClassificationService(
            Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"))
    );

    @Test
    void classifiesFutureTaxonomyFamiliesFromClearTitleEvidence() {
        assertFamily("Frontend Engineer", JobRoleFamily.FRONTEND);
        assertFamily("UX Designer", JobRoleFamily.UX_UI_DESIGN);
        assertFamily("UX Researcher", JobRoleFamily.UX_UI_DESIGN);
        assertFamily("Product Designer", JobRoleFamily.UX_UI_DESIGN);
        assertFamily("Network Engineer", JobRoleFamily.NETWORKING_TELECOM);
        assertFamily("NOC Engineer", JobRoleFamily.NETWORKING_TELECOM);
        assertFamily("Telecom Network Operations Engineer", JobRoleFamily.NETWORKING_TELECOM);
        assertFamily("Technical Project Manager", JobRoleFamily.PROJECT_PROGRAM_DELIVERY);
        assertFamily("IT Project Manager", JobRoleFamily.PROJECT_PROGRAM_DELIVERY);
        assertFamily("Scrum Master", JobRoleFamily.PROJECT_PROGRAM_DELIVERY);
        assertFamily("Technical PMO", JobRoleFamily.PROJECT_PROGRAM_DELIVERY);
        assertFamily("Embedded Software Engineer", JobRoleFamily.EMBEDDED_IOT);
        assertFamily("Firmware Engineer", JobRoleFamily.EMBEDDED_IOT);
        assertFamily("IoT Engineer", JobRoleFamily.EMBEDDED_IOT);
        assertFamily("Unity Developer", JobRoleFamily.GAME_DEVELOPMENT);
        assertFamily("Unreal Engine Developer", JobRoleFamily.GAME_DEVELOPMENT);
        assertFamily("Gameplay Engineer", JobRoleFamily.GAME_DEVELOPMENT);
        assertFamily("Technical Artist", JobRoleFamily.GAME_DEVELOPMENT);
        assertFamily("Solidity Developer", JobRoleFamily.BLOCKCHAIN_WEB3);
        assertFamily("Blockchain Engineer", JobRoleFamily.BLOCKCHAIN_WEB3);
        assertFamily("Smart Contract Developer", JobRoleFamily.BLOCKCHAIN_WEB3);
        assertFamily("Web3 Developer", JobRoleFamily.BLOCKCHAIN_WEB3);
        assertFamily("Solution Architect", JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES);
        assertFamily("Solutions Architect", JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES);
        assertFamily("Pre-Sales Engineer", JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES);
        assertFamily("Implementation Consultant", JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES);
        assertFamily("Technical Consultant", JobRoleFamily.SOLUTIONS_CONSULTING_PRE_SALES);
        assertFamily("Technical Lead", JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE);
        assertFamily("Engineering Manager", JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE);
        assertFamily("Software Architect", JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE);
        assertFamily("Principal Engineer", JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE);
        assertFamily("Staff Engineer", JobRoleFamily.TECHNICAL_LEADERSHIP_ARCHITECTURE);
    }

    @Test
    void usesSoftwareEngineeringGeneralOnlyForClearlyTechnicalGenericTitles() {
        JobClassification result = assertFamily("Software Engineer", JobRoleFamily.SOFTWARE_ENGINEERING_GENERAL);
        assertFamily("Software Developer", JobRoleFamily.SOFTWARE_ENGINEERING_GENERAL);
        assertFamily("Application Developer", JobRoleFamily.SOFTWARE_ENGINEERING_GENERAL);
        assertNull(result.roleSpecialty());
    }

    @Test
    void keepsGenericTitlesUnknownWithoutTechnicalEvidence() {
        assertFamily("Engineer", JobRoleFamily.UNKNOWN);
        assertFamily("Consultant", JobRoleFamily.UNKNOWN);
        assertFamily("Analyst", JobRoleFamily.UNKNOWN);
        assertFamily("Specialist", JobRoleFamily.UNKNOWN);
        assertFamily("Coordinator", JobRoleFamily.UNKNOWN);
        assertFamily("Project Coordinator", JobRoleFamily.UNKNOWN);
        assertFalse(service.classify("Engineer").hasDisplayableFamily());
    }

    @Test
    void keepsUnknownAndOutOfScopeOutOfVisualBadges() {
        JobClassification unknown = service.classify("Engineer");
        JobClassification outOfScope = service.classify("Warehouse Administrative Analyst");

        assertEquals(JobRoleFamily.UNKNOWN, unknown.roleFamily());
        assertEquals(JobRoleFamily.OUT_OF_SCOPE, outOfScope.roleFamily());
        assertFalse(unknown.hasDisplayableFamily());
        assertFalse(outOfScope.hasDisplayableFamily());
    }

    @Test
    void doesNotPromoteIsolatedDescriptionWordsWhenTitleDoesNotSupportThem() {
        assertFamily("Engineer", "React, UX, QA, security and AI platform experience.", JobRoleFamily.UNKNOWN);
    }

    @Test
    void detectsStructuredMetadataWithoutPersistingTechnologies() {
        JobClassification result = service.classify(
                "Senior Backend Engineer",
                "Full-time remote role building APIs.",
                "Argentina Remote"
        );

        assertEquals(JobRoleFamily.BACKEND, result.roleFamily());
        assertEquals("SENIOR", result.roleSeniority());
        assertEquals("REMOTE", result.workModality());
        assertEquals("FULLTIME", result.employmentType());
        assertEquals(NOW, service.classifiedAtNow());
    }

    private JobClassification assertFamily(String title, JobRoleFamily expected) {
        return assertFamily(title, null, expected);
    }

    private JobClassification assertFamily(String title, String description, JobRoleFamily expected) {
        JobClassification classification = service.classify(title, description);
        assertEquals(expected, classification.roleFamily());
        return classification;
    }
}
