package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for RegistrationStatus. */
public enum RegistrationStatus implements DbEnum {

    ACTIVE("Activa", "Activa"),
    COMPLETED("Completada", "Completada"),
    WITHDRAWN("Retirada", "Retirada");

    private final String dbLabel;
    private final String displayName;

    RegistrationStatus(String dbLabel, String displayName) {
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

    public static RegistrationStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(RegistrationStatus.class, label);
    }
}
