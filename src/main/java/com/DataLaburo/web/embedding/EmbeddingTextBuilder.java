package com.DataLaburo.web.embedding;

import com.DataLaburo.web.model.CandidateProfile;
import com.DataLaburo.web.model.Job;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingTextBuilder {
    public String buildForJob(Job job) {
        if (job == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        appendSection(out, "Title", job.getTitle());
        appendSection(out, "Company", job.getCompany());
        appendSection(out, "Location", job.getLocation());

        String description = firstNonBlank(job.getDescription(), job.getVisibleText());
        appendSection(out, "Description", description);
        appendSection(out, "Requirements", job.getRequirementsText());

        return out.toString().trim();
    }

    public String buildForCandidateProfile(CandidateProfile profile) {
        if (profile == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        appendSection(out, "CV", profile.getCvText());
        return out.toString().trim();
    }

    private static void appendSection(StringBuilder out, String label, String value) {
        if (out == null || label == null || label.isBlank() || value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append("\n\n");
        }
        out.append(label).append(":\n").append(value.trim());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return (second != null && !second.isBlank()) ? second : null;
    }
}
