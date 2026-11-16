package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.EventPosition;

import java.util.Objects;

/**
 * One student who took part in one event. In an event with competition the participation also has
 * a position (1er, 2do, 3er Lugar or Participante); it starts as "Participante" and can be changed.
 * In an event without competition there is no position (null).
 */
public class EventParticipation {

    private Integer id;
    private final SchoolEvent event;
    private final Student student;
    private EventPosition position;

    public EventParticipation(SchoolEvent event, Student student) {
        this.event = Objects.requireNonNull(event, "event");
        this.student = Objects.requireNonNull(student, "student");
        this.position = event.hasCompetition() ? EventPosition.PARTICIPANT : null;
    }

    /** Sets the position. It is only valid when the event has a competition. */
    public void assignPosition(EventPosition position) {
        if (!event.hasCompetition()) {
            throw new ValidationException("«" + event.getName() + "» no tiene competencia: no admite posiciones.");
        }
        if (position == null) {
            throw new ValidationException("La posición es obligatoria.");
        }
        this.position = position;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public SchoolEvent getEvent() {
        return event;
    }

    public Student getStudent() {
        return student;
    }

    /** The position, or null when the event has no competition. */
    public EventPosition getPosition() {
        return position;
    }
}
