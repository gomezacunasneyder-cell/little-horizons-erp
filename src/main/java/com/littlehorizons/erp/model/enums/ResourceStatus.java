package com.littlehorizons.erp.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * State of a resource, mirroring the estado_recurso ENUM. It also knows which changes a person
 * may make by hand (see {@link #manualTargets()}): "Prestado" is only set by a loan and
 * "Dado de Baja" is final, so the same rule is never repeated in the views or the controller.
 */
public enum ResourceStatus implements DbEnum {

    AVAILABLE("Disponible"),
    BORROWED("Prestado"),
    MAINTENANCE("En Mantenimiento"),
    WRITTEN_OFF("Dado de Baja");

    private final String label;

    ResourceStatus(String label) {
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

    /**
     * States this one can be changed to by hand, in menu order. A borrowed resource must be
     * returned first (the return sets it Disponible) and a written-off one never comes back.
     */
    public Set<ResourceStatus> manualTargets() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(MAINTENANCE, WRITTEN_OFF);
            case MAINTENANCE -> EnumSet.of(AVAILABLE, WRITTEN_OFF);
            case BORROWED, WRITTEN_OFF -> EnumSet.noneOf(ResourceStatus.class);
        };
    }

    public static ResourceStatus fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(ResourceStatus.class, label);
    }
}
