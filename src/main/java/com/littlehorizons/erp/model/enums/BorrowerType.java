package com.littlehorizons.erp.model.enums;

/**
 * Who asks for a resource: a student or a teacher. The school script keeps one loan table for each
 * (prestamos_estudiante / prestamos_profesor), so the DAO uses this type to choose the table and
 * the rest of the program treats both kinds of loan in the same way.
 */
public enum BorrowerType implements DbEnum {

    STUDENT("Estudiante"),
    TEACHER("Profesor");

    private final String label;

    BorrowerType(String label) {
        this.label = label;
    }

    @Override
    public String getDbLabel() {
        return label;
    }

    @Override
    public String getDisplayName() {
        return label;
    }

    public static BorrowerType fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(BorrowerType.class, label);
    }
}
