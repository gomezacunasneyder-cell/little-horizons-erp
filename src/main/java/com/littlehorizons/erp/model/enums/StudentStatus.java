package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for StudentStatus. */
public enum StudentStatus implements DbEnum {

    ACTIVE("Activo", "Activo"),
    GRADUATED("Graduado", "Graduado"),
    WITHDRAWN("Retirado", "Retirado"),
    SUSPENDED("Suspendido", "Suspendido");

    private final String dbLabel;
    private final String displayName;

    StudentStatus(String dbLabel, String displayName) {
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

    public static StudentStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(StudentStatus.class, label);
    }
}
