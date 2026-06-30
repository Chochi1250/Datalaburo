package com.DataLaburo.web.analysis.knowledge;

import java.util.List;

public record OpportunityKnowledgeDetailView(
        String coverageCode,
        String coverageLabel,
        String roleFamilyLabel,
        String secondaryFocusLabel,
        String secondaryFocusLimit,
        String summary,
        String technicalMatchLimitNote,
        List<SharedSignalItem> sharedSignals,
        List<StrengthItem> strengths,
        List<GapItem> gaps,
        List<TransferItem> transfers,
        List<ActionItem> actions,
        boolean lowContext,
        boolean outOfScope
) {
    public OpportunityKnowledgeDetailView {
        sharedSignals = safe(sharedSignals);
        strengths = safe(strengths);
        gaps = safe(gaps);
        transfers = safe(transfers);
        actions = safe(actions);
    }

    public boolean showStrengths() {
        return !strengths.isEmpty();
    }

    public boolean showGaps() {
        return !gaps.isEmpty();
    }

    public boolean showTransfers() {
        return !transfers.isEmpty();
    }

    public boolean showActions() {
        return !actions.isEmpty();
    }

    public boolean showSecondaryFocus() {
        return secondaryFocusLabel != null && !secondaryFocusLabel.isBlank()
                && secondaryFocusLimit != null && !secondaryFocusLimit.isBlank();
    }

    public boolean showTechnicalMatchLimitNote() {
        return technicalMatchLimitNote != null && !technicalMatchLimitNote.isBlank();
    }

    public boolean showSharedSignals() {
        return !sharedSignals.isEmpty();
    }

    public boolean showSupportCards() {
        return showStrengths() || showTransfers() || showGaps();
    }

    public boolean strengthsFullWidth() {
        return (showStrengths() || showTransfers()) && !showGaps();
    }

    public boolean gapsFullWidth() {
        return showGaps() && !showStrengths() && !showTransfers();
    }

    public boolean singleActionLayout() {
        return actions.size() == 1;
    }

    public boolean compactActionLayout() {
        return actions.size() == 2;
    }

    public boolean timelineActionLayout() {
        return actions.size() >= 3;
    }

    public String actionLayoutClass() {
        if (singleActionLayout()) {
            return "is-single-action";
        }
        if (compactActionLayout()) {
            return "is-two-actions";
        }
        return "is-timeline-action";
    }

    public record SharedSignalItem(
            String skill,
            String evidenceTypeLabel,
            String evidenceTypeCode,
            String warning
    ) {
    }

    public record StrengthItem(
            String skill,
            String evidenceTypeLabel,
            String evidenceTypeCode,
            String explanation,
            String limit
    ) {
    }

    public record GapItem(
            String skill,
            String severityLabel,
            String severityCode,
            String explanation,
            String transferNote,
            String action
    ) {
    }

    public record TransferItem(
            String route,
            List<String> concepts,
            String warning
    ) {
        public TransferItem {
            concepts = safe(concepts);
        }

        public String conceptsText() {
            return String.join(", ", concepts);
        }

        public List<String> visibleConcepts() {
            return concepts.stream().limit(2).toList();
        }
    }

    public record ActionItem(String title, String text, String reason) {
        public ActionItem(String text, String reason) {
            this(null, text, reason);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
