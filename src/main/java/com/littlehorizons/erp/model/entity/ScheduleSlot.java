package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.Weekday;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/** One class block of an assignment: a day of the week and a start and end time. */
public class ScheduleSlot {

    public static final LocalTime SCHOOL_START = LocalTime.of(6, 0);
    public static final LocalTime SCHOOL_END = LocalTime.of(18, 0);
    public static final int MIN_MINUTES = 30;

    private Integer id;
    private final TeachingAssignment assignment;
    private final Weekday day;
    private final LocalTime start;
    private final LocalTime end;

    public ScheduleSlot(TeachingAssignment assignment, Weekday day, LocalTime start, LocalTime end) {
        this.assignment = Objects.requireNonNull(assignment, "assignment");
        this.day = Objects.requireNonNull(day, "day");
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new ValidationException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        if (start.isBefore(SCHOOL_START) || end.isAfter(SCHOOL_END)) {
            throw new ValidationException("La clase debe estar dentro de la jornada escolar (" + SCHOOL_START + " a " + SCHOOL_END + ").");
        }
        if (Duration.between(start, end).toMinutes() < MIN_MINUTES) {
            throw new ValidationException("Una clase debe durar al menos " + MIN_MINUTES + " minutos.");
        }
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public TeachingAssignment getAssignment() { return assignment; }
    public Weekday getDay() { return day; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }

    public long getDurationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    /** Two blocks overlap when they are on the same day and each one starts before the other ends. */
    public boolean overlaps(Weekday otherDay, LocalTime otherStart, LocalTime otherEnd) {
        return day == otherDay && start.isBefore(otherEnd) && end.isAfter(otherStart);
    }

    /** "Lunes 07:00-08:00". */
    public String getTimeText() {
        return day.getDisplayName() + " " + start + "-" + end;
    }
}
