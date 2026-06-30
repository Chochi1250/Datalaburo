package com.DataLaburo.web.analysis.evidence;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.CandidateProfileProject;
import com.DataLaburo.web.model.ProjectEvidenceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionalEvidenceServiceTest {
    private final ProfessionalEvidenceService service = new ProfessionalEvidenceService();

    @Test
    void seniorBackendJavaExperienceCreatesWorkEvidenceAndSeniorDomain() {
        CandidateProfile profile = profile(
                10L,
                """
                Senior backend engineer with 7 years of professional experience building Java and Spring Boot REST APIs.
                Maintained production microservices and PostgreSQL services for client-facing platforms.
                """,
                "Java, Spring Boot, Docker"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "Spring Boot", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA);
        assertEquals("SENIOR", summary.seniorityFor(ProfessionalDomain.BACKEND_JAVA).orElseThrow().seniority());
        assertTrue(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertEquals(ProfessionalEvidenceStrength.STRONG, evidence(summary, "Java").strength());
    }

    @Test
    void traineeWithBackendProjectsGetsProjectEvidenceButNotSeniorWorkExperience() {
        CandidateProfile profile = profile(
                11L,
                "Trainee backend developer completing university coursework.",
                "Java, Spring Boot, PostgreSQL"
        );
        CandidateProfileProject project = project(
                "Task API",
                """
                REST API for task management with authentication, persistence, Docker setup and integration tests.
                Includes endpoints, validations and PostgreSQL schema scripts.
                """,
                "Java, Spring Boot, PostgreSQL, Docker",
                ProjectEvidenceType.PERSONAL_PROJECT,
                "https://github.com/example/task-api",
                null
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of(project));

        assertEvidence(summary, "Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "Spring Boot", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA);
        assertTrue(summary.transitionDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertFalse(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertFalse(isSenior(summary, ProfessionalDomain.BACKEND_JAVA));
        assertTrue(evidence(summary, "Java").warnings().contains("Project evidence does not equal senior work experience."));
    }

    @Test
    void traineeCareerWithoutProjectsGetsAcademicEvidence() {
        CandidateProfile profile = profile(
                12L,
                """
                Computer Science student at university. Coursework in Java, SQL and software engineering.
                Looking for a trainee backend opportunity.
                """,
                "Java, SQL, Spring Boot"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "Java", ProfessionalEvidenceType.ACADEMIC, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "SQL", ProfessionalEvidenceType.ACADEMIC, ProfessionalDomain.DATA);
        assertEvidence(summary, "Spring Boot", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.BACKEND_JAVA);
        assertFalse(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertEquals("TRAINEE", summary.seniorityFor(ProfessionalDomain.BACKEND_JAVA).orElseThrow().seniority());
    }

    @Test
    void supportOneYearWithBackendProjectKeepsSupportAsWorkAndBackendAsTransition() {
        CandidateProfile profile = profile(
                13L,
                """
                IT Support analyst with 1 year of professional experience resolving incidents,
                service desk tickets and Windows support for internal users.
                """,
                "IT Support, Java, Spring Boot"
        );
        CandidateProfileProject project = project(
                "Helpdesk automation API",
                "Personal backend API for ticket triage with Java, Spring Boot, REST APIs and PostgreSQL.",
                "Java, Spring Boot, PostgreSQL",
                ProjectEvidenceType.PERSONAL_PROJECT,
                "https://github.com/example/helpdesk-api",
                null
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of(project));

        assertEvidence(summary, "IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT);
        assertEvidence(summary, "Java", ProfessionalEvidenceType.PROJECT, ProfessionalDomain.BACKEND_JAVA);
        assertEquals("JUNIOR", summary.seniorityFor(ProfessionalDomain.SUPPORT).orElseThrow().seniority());
        assertTrue(summary.transitionDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertFalse(isSenior(summary, ProfessionalDomain.BACKEND_JAVA));
    }

    @Test
    void seniorSupportAndInfraDoNotMakeBackendSenior() {
        CandidateProfile profile = profile(
                14L,
                """
                Senior IT support and infrastructure analyst with 7 years of professional experience.
                Maintained Windows Server, Active Directory and networking operations.
                Junior backend training with Java basics.
                """,
                "IT Support, Windows Server, Active Directory, Java"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEquals("SENIOR", summary.seniorityFor(ProfessionalDomain.SUPPORT).orElseThrow().seniority());
        assertEquals("SENIOR", summary.seniorityFor(ProfessionalDomain.INFRA).orElseThrow().seniority());
        assertEvidence(summary, "Java", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.BACKEND_JAVA);
        assertFalse(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertFalse(isSenior(summary, ProfessionalDomain.BACKEND_JAVA));
    }

    @Test
    void supportStorageAndInfraSignalsBecomeWorkEvidenceOnlyWhenOperationalContextExists() {
        CandidateProfile profile = profile(
                18L,
                """
                IT Support and infrastructure analyst with 3 years of professional experience.
                Resolved incident tickets, performed Linux troubleshooting and maintained OpenShift,
                enterprise storage, DS8000, FlashSystem and servers for internal operations.
                """,
                "IT Support, OpenShift, Linux, Storage, Docker, Git"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT);
        assertEvidence(summary, "Tickets", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT);
        assertEvidence(summary, "Linux", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.INFRA);
        assertEvidence(summary, "OpenShift", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.INFRA);
        assertEvidence(summary, "Storage", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.INFRA);
        assertTrue(summary.strongDomains().contains(ProfessionalDomain.SUPPORT));
        assertTrue(summary.strongDomains().contains(ProfessionalDomain.INFRA));
    }

    @Test
    void declaredInfraToolsDoNotBecomeWorkEvidenceFromNearbyGenericExperience() {
        CandidateProfile profile = profile(
                19L,
                """
                IT Support analyst with 1 year of professional experience resolving user incidents.
                Technical skills: OpenShift, Linux, Docker and Git.
                """,
                "IT Support, OpenShift, Linux, Docker, Git"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "IT Support", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.SUPPORT);
        assertEvidence(summary, "OpenShift", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.INFRA);
        assertEvidence(summary, "Linux", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.INFRA);
        assertEvidence(summary, "Docker", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.CLOUD);
        assertEvidence(summary, "Git", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.BACKEND_JAVA);
    }

    @Test
    void seniorDotnetMigratingToJavaKeepsJavaTransferableNotWorkExperience() {
        CandidateProfile profile = profile(
                15L,
                """
                Senior .NET/C# backend engineer with 8 years of professional experience designing ASP.NET services.
                Transitioning to Java and Spring Boot roles.
                """,
                "C#, .NET, Java, Spring Boot"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "C#", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_DOTNET);
        assertEvidence(summary, ".NET", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_DOTNET);
        assertEvidence(summary, "Java", ProfessionalEvidenceType.TRANSFERABLE, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "Spring Boot", ProfessionalEvidenceType.TRANSFERABLE, ProfessionalDomain.BACKEND_JAVA);
        assertEquals("SENIOR", summary.seniorityFor(ProfessionalDomain.BACKEND_DOTNET).orElseThrow().seniority());
        assertFalse(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
        assertFalse(isSenior(summary, ProfessionalDomain.BACKEND_JAVA));
        assertTrue(evidence(summary, "Java").warnings().contains("Transferable signal, not direct evidence."));
    }

    @Test
    void declaredSkillsWithoutEvidenceRemainWeakDeclaredOnlySignals() {
        CandidateProfile profile = profile(
                16L,
                "Technology enthusiast looking for a first technology role.",
                "Java, Spring Boot, Kubernetes, AWS, React, Security"
        );

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "Java", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "Spring Boot", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.BACKEND_JAVA);
        assertEvidence(summary, "Kubernetes", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.CLOUD);
        assertEvidence(summary, "Cloud", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.CLOUD);
        assertEvidence(summary, "React", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.FRONTEND);
        assertEvidence(summary, "Security", ProfessionalEvidenceType.DECLARED_ONLY, ProfessionalDomain.SECURITY);
        assertTrue(summary.skillEvidence().stream()
                .filter(evidence -> evidence.evidenceType() == ProfessionalEvidenceType.DECLARED_ONLY)
                .allMatch(evidence -> evidence.strength() == ProfessionalEvidenceStrength.WEAK));
        assertTrue(summary.strongDomains().isEmpty());
    }

    @Test
    void seniorEvidenceIsPreservedEvenWhenProfileTargetsJuniorRoles() {
        CandidateProfile profile = profile(
                17L,
                "Senior backend engineer with 6 years of professional experience implementing Java and Spring Boot services.",
                "Java, Spring Boot"
        );
        profile.setTargetSeniority("JUNIOR");

        ProfileEvidenceSummary summary = service.summarizeProfile(profile, List.of());

        assertEvidence(summary, "Java", ProfessionalEvidenceType.WORK_EXPERIENCE, ProfessionalDomain.BACKEND_JAVA);
        assertEquals("SENIOR", summary.seniorityFor(ProfessionalDomain.BACKEND_JAVA).orElseThrow().seniority());
        assertTrue(summary.strongDomains().contains(ProfessionalDomain.BACKEND_JAVA));
    }

    private static CandidateProfile profile(Long id, String cvText, String declaredSkillsText) {
        CandidateProfile profile = new CandidateProfile();
        profile.setId(id);
        profile.setName("DIAG " + id);
        profile.setCvText(cvText);
        profile.setDeclaredSkillsText(declaredSkillsText);
        profile.setTargetRole("BACKEND");
        profile.setTargetSeniority("ANY");
        profile.setSearchMode("FOCUSED");
        return profile;
    }

    private static CandidateProfileProject project(
            String title,
            String description,
            String skillsText,
            ProjectEvidenceType evidenceType,
            String repositoryUrl,
            String demoUrl
    ) {
        CandidateProfileProject project = new CandidateProfileProject();
        project.setTitle(title);
        project.setDescription(description);
        project.setSkillsText(skillsText);
        project.setEvidenceType(evidenceType);
        project.setRepositoryUrl(repositoryUrl);
        project.setDemoUrl(demoUrl);
        return project;
    }

    private static ProfessionalSkillEvidence evidence(ProfileEvidenceSummary summary, String skill) {
        return summary.strongestEvidenceFor(skill).orElseThrow();
    }

    private static void assertEvidence(
            ProfileEvidenceSummary summary,
            String skill,
            ProfessionalEvidenceType evidenceType,
            ProfessionalDomain domain
    ) {
        ProfessionalSkillEvidence evidence = evidence(summary, skill);
        assertEquals(evidenceType, evidence.evidenceType());
        assertEquals(domain, evidence.domain());
    }

    private static boolean isSenior(ProfileEvidenceSummary summary, ProfessionalDomain domain) {
        return summary.seniorityFor(domain)
                .map(seniority -> seniority.seniority().equals("SENIOR"))
                .orElse(false);
    }
}
