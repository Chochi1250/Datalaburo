package com.DataLaburo.web.analysis;

public record SkillEquivalenceSignal(
        String candidateSkill,
        String targetSkill,
        String relation,
        String reason
) {
}
