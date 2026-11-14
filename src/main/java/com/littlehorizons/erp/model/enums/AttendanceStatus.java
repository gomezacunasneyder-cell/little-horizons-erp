package com.littlehorizons.erp.model.enums;

/**
 * Mirrors the MySQL ENUM used by the database for the attendance state. Besides the labels it
 * knows how it counts in the statistics, so that rule is written once here and not repeated with
 * if/else chains in every report.
 */
public enum AttendanceStatus implements DbEnum {

    PRESENT("Presente", "Presente"),
    ABSENT("Ausente", "Ausente"),
    LATE("Tardanza", "Tardanza"),
    MEDICAL_EXCUSE("Excusa Médica", "Excusa Médica");

    private final String dbLabel;
    private final String displayName;

    AttendanceStatus(String dbLabel, String displayName) {
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

    /** True only for "Ausente": an absence with a medical excuse is not a concerning absence. */
    public boolean isUnexcusedAbsence() {
        return this == ABSENT;
    }

    /** The student was in class (on time or late). */
    public boolean wasInClass() {
        return this == PRESENT || this == LATE;
    }

    public static AttendanceStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(AttendanceStatus.class, label);
    }
}
