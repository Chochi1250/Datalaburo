package com.DataLaburo.web.service;

public enum JobRoleFamily {
    BACKEND("Backend", true),
    FRONTEND("Frontend", true),
    FULL_STACK("Full Stack", true),
    SOFTWARE_ENGINEERING_GENERAL("Software Engineering", true),

    DATA("Data", true),
    AI_ML_AUTOMATION("AI/ML Automation", true),

    CLOUD_DEVOPS_SRE("Cloud/DevOps/SRE", true),
    INFRASTRUCTURE_SUPPORT("Infrastructure/Support", true),
    NETWORKING_TELECOM("Networking/Telecom", true),
    SECURITY("Security", true),
    DATABASE("Database", true),

    QA("QA", true),

    ERP_CRM_ENTERPRISE("ERP/CRM Enterprise", true),
    PRODUCT_BUSINESS_ANALYSIS("Product/Business Analysis", true),
    PROJECT_PROGRAM_DELIVERY("Project/Program Delivery", true),
    UX_UI_DESIGN("UX/UI Design", true),

    MOBILE("Mobile", true),
    EMBEDDED_IOT("Embedded/IoT", true),
    GAME_DEVELOPMENT("Game Development", true),
    BLOCKCHAIN_WEB3("Blockchain/Web3", true),

    SOLUTIONS_CONSULTING_PRE_SALES("Solutions/Consulting/Pre-Sales", true),
    TECHNICAL_LEADERSHIP_ARCHITECTURE("Technical Leadership/Architecture", true),

    UNKNOWN("Unknown", false),
    OUT_OF_SCOPE("Out of Scope", false);

    private final String label;
    private final boolean displayable;

    JobRoleFamily(String label, boolean displayable) {
        this.label = label;
        this.displayable = displayable;
    }

    public String label() {
        return label;
    }

    public boolean isDisplayable() {
        return displayable;
    }
}
