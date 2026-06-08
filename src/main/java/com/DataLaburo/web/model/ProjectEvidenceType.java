package com.DataLaburo.web.model;

public enum ProjectEvidenceType {
    ACADEMIC_PROJECT("Proyecto academico"),
    PERSONAL_PROJECT("Proyecto personal"),
    WORK_PROJECT("Proyecto laboral"),
    COURSE_PROJECT("Proyecto de curso"),
    OTHER("Otro");

    private final String label;

    ProjectEvidenceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
