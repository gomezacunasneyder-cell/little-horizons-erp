package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.Weekday;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A club of the school catalog (Science, Robotics...). It always has a name, a description, a
 * teacher in charge, a maximum capacity and a meeting schedule. Every setter validates BEFORE
 * changing the value, so an invalid Club cannot exist in memory. How many students are inside
 * right now is not stored here: it is counted from the memberships (see ClubOccupancy).
 */
public class Club {

    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int SCHEDULE_MAX = 100;
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 200;

    private Integer id;
    private String name;
    private String description;
    private Teacher supervisor;
    private int maxCapacity;
    private String meetingSchedule;
    private boolean active = true;

    public Club(String name, String description, Teacher supervisor, int maxCapacity, String meetingSchedule) {
        setName(name);
        setDescription(description);
        setSupervisor(supervisor);
        setMaxCapacity(maxCapacity);
        setMeetingSchedule(meetingSchedule);
    }

    /** Builds the text stored as the schedule, for example "Martes 15:00-17:00". */
    public static String formatSchedule(Weekday day, LocalTime start, LocalTime end) {
        if (day == null || start == null || end == null) {
            throw new ValidationException("El horario necesita día, hora de inicio y hora de fin.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("La hora de fin debe ser posterior a la de inicio.");
        }
        return day.getDisplayName() + " " + start + "-" + end;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    /** Letters only (the same rule as the rest of the descriptive texts of the project). */
    public void setName(String name) {
        this.name = Person.requireLabel(name, NAME_MAX, "El nombre del club");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = checkDescription(description);
    }

    /** The rule of the description, public so the form can validate the answer while it is typed. */
    public static String checkDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new ValidationException("La descripción del club es obligatoria.");
        }
        String clean = description.trim();
        if (clean.length() > DESCRIPTION_MAX) {
            throw new ValidationException("La descripción admite máximo " + DESCRIPTION_MAX + " caracteres.");
        }
        return clean;
    }

    public Teacher getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(Teacher supervisor) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        if (maxCapacity < MIN_CAPACITY || maxCapacity > MAX_CAPACITY) {
            throw new ValidationException("El cupo debe estar entre " + MIN_CAPACITY + " y " + MAX_CAPACITY + " estudiantes.");
        }
        this.maxCapacity = maxCapacity;
    }

    public String getMeetingSchedule() {
        return meetingSchedule;
    }

    public void setMeetingSchedule(String meetingSchedule) {
        if (meetingSchedule == null || meetingSchedule.isBlank()) {
            throw new ValidationException("El horario de reunión es obligatorio.");
        }
        String clean = meetingSchedule.trim();
        if (clean.length() > SCHEDULE_MAX) {
            throw new ValidationException("El horario admite máximo " + SCHEDULE_MAX + " caracteres.");
        }
        this.meetingSchedule = clean;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return name;
    }
}
