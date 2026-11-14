package com.littlehorizons.erp.model.enums;

/** State of a teacher, mirroring the ENUM of the profesores table. */
public enum TeacherStatus implements DbEnum {
    ACTIVE("Activo", "Activo"),
    INACTIVE("Inactivo", "Inactivo"),
    ON_LEAVE("Licencia", "En licencia");

    private final String dbLabel;
    private final String displayName;

    TeacherStatus(String dbLabel, String displayName) {
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

    public static TeacherStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(TeacherStatus.class, label);
    }
}
