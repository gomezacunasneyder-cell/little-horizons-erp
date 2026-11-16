package com.littlehorizons.erp.model.enums;

/** Result of a participant in an event with competition, mirroring the posicion_obtenida ENUM. The declaration order is the ranking order. */
public enum EventPosition implements DbEnum {

    FIRST("1er Lugar"),
    SECOND("2do Lugar"),
    THIRD("3er Lugar"),
    PARTICIPANT("Participante");

    private final String label;

    EventPosition(String label) {
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

    /** True for the three podium places (1st, 2nd, 3rd); "Participante" is not a podium place. */
    public boolean isPodium() {
        return this != PARTICIPANT;
    }

    public static EventPosition fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(EventPosition.class, label);
    }
}
