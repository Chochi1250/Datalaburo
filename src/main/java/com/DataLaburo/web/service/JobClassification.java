package com.DataLaburo.web.service;

import java.util.Objects;

public record JobClassification(
        JobRoleFamily roleFamily,
        String roleSpecialty,
        String roleSeniority,
        String workModality,
        String employmentType
) {
    public JobClassification {
        Objects.requireNonNull(roleFamily, "roleFamily");
        if (roleSpecialty != null && roleSpecialty.isBlank()) {
            roleSpecialty = null;
        }
        if (roleSeniority != null && roleSeniority.isBlank()) {
            roleSeniority = null;
        }
        if (workModality != null && workModality.isBlank()) {
            workModality = null;
        }
        if (employmentType != null && employmentType.isBlank()) {
            employmentType = null;
        }
    }

    public boolean hasDisplayableFamily() {
        return roleFamily.isDisplayable();
    }
}
