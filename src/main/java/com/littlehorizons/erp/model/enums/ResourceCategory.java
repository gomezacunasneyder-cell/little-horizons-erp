package com.littlehorizons.erp.model.enums;

/**
 * Kind of lendable resource, mirroring the rows of the categorias_recurso table (the school
 * script inserts these five names). The database stores the name, so each constant carries it.
 */
public enum ResourceCategory implements DbEnum {

    BOOK("Libro"),
    LAB_EQUIPMENT("Equipo de Laboratorio"),
    SPORTS_EQUIPMENT("Equipo Deportivo"),
    TECH_DEVICE("Dispositivo Tecnológico"),
    TEACHING_MATERIAL("Material Didáctico");

    private final String label;

    ResourceCategory(String label) {
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

    public static ResourceCategory fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(ResourceCategory.class, label);
    }
}
