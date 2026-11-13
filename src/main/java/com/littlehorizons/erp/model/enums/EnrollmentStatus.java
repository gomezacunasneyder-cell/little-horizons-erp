package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for EnrollmentStatus. */
public enum EnrollmentStatus implements DbEnum {

    ACTIVE("Activa", "Activa"),
    COMPLETED("Completada", "Completada"),
    WITHDRAWN("Retirada", "Retirada");

    private final String dbLabel;
    private final String displayName;

    EnrollmentStatus(String dbLabel, String displayName) {
        this.dbLabel = dbLabel;
        this.displayName = displayName;
    }

    @Override
    public String getDbLabel() {
        return dbLabel;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public static EnrollmentStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(EnrollmentStatus.class, label);
    }
}
