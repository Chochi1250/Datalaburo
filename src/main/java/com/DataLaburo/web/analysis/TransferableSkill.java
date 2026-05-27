package com.DataLaburo.web.analysis;

public record TransferableSkill(
        String from,
        String to,
        TransferStrength strength,
        String reason
) {
}
