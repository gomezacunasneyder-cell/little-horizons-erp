package com.littlehorizons.erp.controller.event;

/** Where an event is in time. It is calculated from the date and the cancelled flag; it is not stored. */
public enum EventStatus {

    UPCOMING("Próximo"),
    TODAY("Hoy"),
    HELD("Realizado"),
    CANCELLED("Cancelado");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
