package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.entity.TeachingAssignment;

import java.util.List;

/**
 * Academic load of a teacher in one year: for each assignment, the hours per week that are
 * already scheduled, and the total. It is a plain data holder (a Java record) built by the
 * controller so the view only prints it.
 */
public record TeacherWorkload(List<Line> lines, double totalWeeklyHours) {

    /** One assignment and its scheduled hours per week. */
    public record Line(TeachingAssignment assignment, double weeklyHours) {
    }
}
