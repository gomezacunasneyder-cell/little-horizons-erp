package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for Gender. */
public enum Gender implements DbEnum {

    MALE("M", "Masculino"),
    FEMALE("F", "Femenino"),
    OTHER("Otro", "Otro");

    private final String dbLabel;
    private final String displayName;

    Gender(String dbLabel, String displayName) {
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

    public static Gender fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(Gender.class, label);
    }
}
