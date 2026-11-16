package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.event.EventAnalyzer;
import com.littlehorizons.erp.controller.event.EventResults;
import com.littlehorizons.erp.controller.event.StudentEventHistory;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.EventDao;
import com.littlehorizons.erp.model.dao.EventParticipationDao;
import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.EventPosition;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;

/**
 * Rules for registering who took part in an event and, in competitions, the position of each one:
 * only ACTIVE students, only events that were not cancelled and whose date has arrived (the
 * system records who PARTICIPATED), never the same student twice in the same event, and positions
 * only in events with a competition. Several students can share a position (team events, ties).
 */
public class EventParticipationController extends BaseController {

    private final EventParticipationDao participationDao;
    private final EventDao eventDao;

    public EventParticipationController(EventParticipationDao participationDao, EventDao eventDao, ErrorLogger logger) {
        super(logger);
        this.participationDao = participationDao;
        this.eventDao = eventDao;
    }

    /**
     * Registers a participant. In an event with competition the position can be given (null means
     * "Participante"); in an event without competition it must be null.
     */
    public Result<EventParticipation> register(Student student, SchoolEvent event, EventPosition position) {
        return execute("Register event participant", () -> {
            if (student.getStatus() != StudentStatus.ACTIVE) {
                throw new ValidationException("Solo los estudiantes activos pueden registrarse como participantes ("
                        + student.getFullName() + " está " + student.getStatus().getDisplayName().toLowerCase() + ").");
            }
            // read the event again: it may have been edited or cancelled since the user picked it
            SchoolEvent current = eventDao.findById(event.getId()).orElse(null);
            if (current == null) {
                throw new ValidationException("El evento ya no existe.");
            }
            if (!current.isActive()) {
                throw new ValidationException("El evento «" + current.getName() + "» está cancelado.");
            }
            if (current.getDate().isAfter(LocalDate.now())) {
                throw new ValidationException("El evento «" + current.getName() + "» es el " + current.getDate()
                        + ": solo se registran participantes cuando el evento ya llegó.");
            }
            if (participationDao.exists(current.getId(), student.getId())) {
                throw new ValidationException(student.getFullName() + " ya está registrado en «" + current.getName() + "».");
            }
            EventParticipation participation = new EventParticipation(current, student);
            if (position != null) {
                participation.assignPosition(position); // fails if the event has no competition
            }
            participationDao.create(participation);
            return Result.ok(participation, student.getFullName() + " registrado en «" + current.getName()
                    + (participation.getPosition() == null ? "»." : "» (" + participation.getPosition().getDisplayName() + ")."));
        });
    }

    /** Changes the position of a participant (a correction or the result once the competition ended). */
    public Result<EventParticipation> assignPosition(EventParticipation participation, EventPosition position) {
        return execute("Assign event position", () -> {
            EventPosition previous = participation.getPosition();
            participation.assignPosition(position); // validates: the event must have a competition
            try {
                participationDao.updatePosition(participation);
            } catch (RuntimeException | DaoException e) {
                if (previous != null) {
                    participation.assignPosition(previous); // keep the object equal to the database
                }
                throw e;
            }
            return Result.ok(participation, participation.getStudent().getFullName() + ": "
                    + position.getDisplayName() + " en «" + participation.getEvent().getName() + "».");
        });
    }

    public Result<EventParticipation> remove(EventParticipation participation) {
        return execute("Remove event participant", () -> {
            participationDao.delete(participation);
            return Result.ok(participation, participation.getStudent().getFullName() + " quitado de «"
                    + participation.getEvent().getName() + "».");
        });
    }

    /** The participants of an event sorted by position, with how many got each one. */
    public Result<EventResults> results(SchoolEvent event) {
        return execute("Show event results", () -> Result.ok(
                EventAnalyzer.results(event, participationDao.findByEvent(event.getId())), ""));
    }

    /** Every event a student took part in, newest first, with the number of podium places. */
    public Result<StudentEventHistory> historyOf(Student student) {
        return execute("Show student events", () -> Result.ok(
                EventAnalyzer.history(participationDao.findByStudent(student.getId())), ""));
    }
}
