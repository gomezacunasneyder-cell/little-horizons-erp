package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for DocumentType. */
public enum DocumentType implements DbEnum {

    TI("TI", "Tarjeta de identidad"),
    CC("CC", "Cédula de ciudadanía"),
    CE("CE", "Cédula de extranjería"),
    PASSPORT("Pasaporte", "Pasaporte");

    private final String dbLabel;
    private final String displayName;

    DocumentType(String dbLabel, String displayName) {
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

    public static DocumentType fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(DocumentType.class, label);
    }
}
