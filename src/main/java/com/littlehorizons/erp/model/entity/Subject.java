package com.littlehorizons.erp.model.entity;

import java.util.Objects;

/** A subject taught in one specific grade (code like MAT-5). */
public class Subject {

    private final int id;
    private final String code;
    private final String name;
    private final GradeLevel gradeLevel;
    private final int weeklyHours;
    private final boolean active;

    public Subject(int id, String code, String name, GradeLevel gradeLevel, int weeklyHours, boolean active) {
        this.id = id;
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel");
        this.weeklyHours = weeklyHours;
        this.active = active;
    }

    public int getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public GradeLevel getGradeLevel() { return gradeLevel; }
    public int getWeeklyHours() { return weeklyHours; }
    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
