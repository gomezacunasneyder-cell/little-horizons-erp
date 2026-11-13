package com.littlehorizons.erp.model.enums;

/** Mirrors the MySQL ENUM used by the database for AcademicPeriod. */
public enum AcademicPeriod implements DbEnum {

    FIRST("I", "Período I"),
    SECOND("II", "Período II"),
    THIRD("III", "Período III"),
    FOURTH("IV", "Período IV"),
    FINAL("Final", "Final");

    private final String dbLabel;
    private final String displayName;

    AcademicPeriod(String dbLabel, String displayName) {
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

    /** Periods in which a student can be registered in a subject (I to IV, not "Final"). */
    public static java.util.List<AcademicPeriod> teachingPeriods() {
        return java.util.List.of(FIRST, SECOND, THIRD, FOURTH);
    }

    public static AcademicPeriod fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(AcademicPeriod.class, label);
    }
}
