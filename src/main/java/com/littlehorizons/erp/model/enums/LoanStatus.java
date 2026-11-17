package com.littlehorizons.erp.model.enums;

/**
 * State of a loan, mirroring the estado_prestamo ENUM of both loan tables. "Activo" and "Vencido"
 * are both OPEN loans (the resource has not come back); the only difference is that the scheduled
 * return date has passed in the second one. "Devuelto" is the final state.
 */
public enum LoanStatus implements DbEnum {

    ACTIVE("Activo"),
    RETURNED("Devuelto"),
    OVERDUE("Vencido");

    private final String label;

    LoanStatus(String label) {
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

    /** True while the resource is still out: Activo or Vencido. */
    public boolean isOpen() {
        return this != RETURNED;
    }

    public static LoanStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(LoanStatus.class, label);
    }
}
