package com.littlehorizons.erp.model.entity;

/** A school grade (Preescolar, Primero ... Once). The numeric level orders them: 0 to 11. */
public class GradeLevel {

    private final int id;
    private final String name;
    private final int level;

    public GradeLevel(int id, String name, int level) {
        this.id = id;
        this.name = name;
        this.level = level;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return name;
    }
}
