package com.littlehorizons.erp.model.enums;

/** Kind of school event, mirroring the tipo_evento ENUM. */
public enum EventType implements DbEnum {

    ACADEMIC("Académico"),
    CULTURAL("Cultural"),
    SPORTS("Deportivo"),
    SOCIAL("Social");

    private final String label;

    EventType(String label) {
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

    public static EventType fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(EventType.class, label);
    }
}
