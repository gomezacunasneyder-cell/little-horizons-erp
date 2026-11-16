package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.EventType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An event of the school calendar (Science Fair, Culture Day, Sports Day...). It has a name, a
 * description, a type, a date and a teacher who organizes it. Some events have a competition, and
 * then each participant gets a position. Every setter validates BEFORE changing the value, so an
 * invalid event cannot exist in memory. (Named SchoolEvent to avoid confusion with java.awt.Event.)
 */
public class SchoolEvent {

    public static final int NAME_MAX = 150;
    public static final int DESCRIPTION_MAX = 500;
    public static final int PLACE_MAX = 150;
    /** An event cannot be more than this many years ahead (a typing mistake such as 2062 is caught). */
    public static final int MAX_YEARS_AHEAD = 5;

    private Integer id;
    private String name;
    private String description;
    private EventType type;
    private LocalDate date;
    private String place;
    private boolean competition;
    private Teacher organizer;
    private boolean active = true;

    public SchoolEvent(String name, String description, EventType type, LocalDate date, String place,
                       boolean competition, Teacher organizer) {
        setName(name);
        setDescription(description);
        setType(type);
        setDate(date);
        setPlace(place);
        this.competition = competition;
        setOrganizer(organizer);
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

    /** Letters only, like the other descriptive texts (the year is already in the date). */
    public void setName(String name) {
        this.name = Person.requireLabel(name, NAME_MAX, "El nombre del evento");
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
            throw new ValidationException("La descripción del evento es obligatoria.");
        }
        String clean = description.trim();
        if (clean.length() > DESCRIPTION_MAX) {
            throw new ValidationException("La descripción admite máximo " + DESCRIPTION_MAX + " caracteres.");
        }
        return clean;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = checkDate(date);
    }

    /** Past events can be registered (to record their results); far-future dates are rejected. */
    public static LocalDate checkDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha del evento es obligatoria.");
        }
        if (date.getYear() < Enrollment.MIN_YEAR) {
            throw new ValidationException("El año debe ser " + Enrollment.MIN_YEAR + " o posterior.");
        }
        if (date.isAfter(LocalDate.now().plusYears(MAX_YEARS_AHEAD))) {
            throw new ValidationException("La fecha no puede estar a más de " + MAX_YEARS_AHEAD + " años en el futuro.");
        }
        return date;
    }

    public String getPlace() {
        return place;
    }

    /** Optional (null when not given); letters only. */
    public void setPlace(String place) {
        this.place = Person.optionalLabel(place, PLACE_MAX, "El lugar");
    }

    public boolean hasCompetition() {
        return competition;
    }

    public void setCompetition(boolean competition) {
        this.competition = competition;
    }

    public Teacher getOrganizer() {
        return organizer;
    }

    public void setOrganizer(Teacher organizer) {
        this.organizer = Objects.requireNonNull(organizer, "organizer");
    }

    /** False means the event was cancelled. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return name + " (" + date + ")";
    }
}
