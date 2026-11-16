package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.event.EventAnalyzer;
import com.littlehorizons.erp.controller.event.EventCalendar;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.EventDao;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;

/**
 * Rules of the event calendar: the organizer is an ACTIVE teacher, the same event name is not
 * repeated on the same date, a competition cannot be removed once it has podium places, and an
 * event can only be cancelled while nobody is registered in it.
 */
public class EventController extends BaseController {

    private final EventDao eventDao;

    public EventController(EventDao eventDao, ErrorLogger logger) {
        super(logger);
        this.eventDao = eventDao;
    }

    public Result<SchoolEvent> create(SchoolEvent event) {
        return execute("Create event", () -> {
            requireActiveOrganizer(event.getOrganizer());
            requireFreeNameAndDate(event);
            eventDao.create(event);
            return Result.ok(event, "Evento «" + event.getName() + "» registrado para el " + event.getDate() + ".");
        });
    }

    /** Saves the edited data of an existing event (the SchoolEvent carries its id and its current state). */
    public Result<SchoolEvent> update(SchoolEvent event) {
        return execute("Update event", () -> {
            requireActiveOrganizer(event.getOrganizer());
            requireFreeNameAndDate(event);
            eventDao.update(event); // the DAO refuses, inside its transaction, to remove a competition that has podium places
            return Result.ok(event, "Evento «" + event.getName() + "» actualizado.");
        });
    }

    /** Cancels or restores an event. Cancelling needs zero participants: remove them first. */
    public Result<SchoolEvent> setActive(SchoolEvent event, boolean active) {
        return execute(active ? "Restore event" : "Cancel event", () -> {
            if (event.isActive() == active) {
                throw new ValidationException("El evento ya está " + (active ? "activo." : "cancelado."));
            }
            if (active) {
                requireActiveOrganizer(event.getOrganizer());
            } else {
                int participants = eventDao.countParticipants(event.getId());
                if (participants > 0) {
                    throw new ValidationException("No se puede cancelar: el evento tiene " + participants
                            + " participante(s) registrado(s). Quítelos primero.");
                }
            }
            event.setActive(active);
            try {
                eventDao.update(event);
            } catch (RuntimeException | DaoException e) {
                event.setActive(!active); // the database kept the old value, so the object must too
                throw e;
            }
            return Result.ok(event, "Evento «" + event.getName() + "» " + (active ? "reactivado." : "cancelado."));
        });
    }

    public Result<EventCalendar> calendar() {
        return execute("Show event calendar", () -> Result.ok(
                EventAnalyzer.calendar(eventDao.findAll(), eventDao.countParticipantsByEvent(), LocalDate.now()), ""));
    }

    /** True when another event already has that name on that date (excludeId = the event being edited, or null). */
    public Result<Boolean> nameAndDateTaken(String name, LocalDate date, Integer excludeId) {
        return execute("Check event name", () -> Result.ok(eventDao.nameAndDateExist(name.trim(), date, excludeId), ""));
    }

    public Result<List<SchoolEvent>> search(String term) {
        return execute("Search events", () -> Result.ok(eventDao.search(requireTerm(term)), ""));
    }

    /** Same search but only events that were not cancelled and have a competition (to assign positions). */
    public Result<List<SchoolEvent>> searchCompetitions(String term) {
        return execute("Search competitions", () -> Result.ok(eventDao.search(requireTerm(term)).stream()
                .filter(e -> e.isActive() && e.hasCompetition()).toList(), ""));
    }

    private static String requireTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ValidationException("Escriba parte del nombre del evento, de su descripción, del lugar o del profesor.");
        }
        return term.trim();
    }

    private void requireFreeNameAndDate(SchoolEvent event) throws DaoException {
        if (eventDao.nameAndDateExist(event.getName(), event.getDate(), event.getId())) {
            throw new ValidationException("Ya existe el evento «" + event.getName() + "» el " + event.getDate() + ".");
        }
    }

    private static void requireActiveOrganizer(Teacher teacher) {
        if (!teacher.isActive()) {
            throw new ValidationException("El profesor " + teacher.getFullName() + " está "
                    + teacher.getStatus().getDisplayName().toLowerCase() + ". Elija un profesor activo.");
        }
    }
}
