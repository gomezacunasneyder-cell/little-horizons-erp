package com.littlehorizons.erp.model.enums;

/** State of a student's membership in a club, mirroring the ENUM of the membresias_club table. */
public enum MembershipStatus implements DbEnum {

    ACTIVE("Activo", "Activo"),
    WITHDRAWN("Retirado", "Retirado");

    private final String dbLabel;
    private final String displayName;

    MembershipStatus(String dbLabel, String displayName) {
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

    public static MembershipStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(MembershipStatus.class, label);
    }
}
