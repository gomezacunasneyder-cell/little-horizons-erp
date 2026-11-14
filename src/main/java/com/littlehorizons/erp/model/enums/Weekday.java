package com.littlehorizons.erp.model.enums;

/** Day of the school week, mirroring the dia_semana ENUM. The declaration order is the sort order. */
public enum Weekday implements DbEnum {
    MONDAY("Lunes"),
    TUESDAY("Martes"),
    WEDNESDAY("Miércoles"),
    THURSDAY("Jueves"),
    FRIDAY("Viernes"),
    SATURDAY("Sábado");

    private final String label;

    Weekday(String label) {
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

    public static Weekday fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(Weekday.class, label);
    }
}
