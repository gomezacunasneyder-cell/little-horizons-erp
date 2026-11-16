package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.SchoolEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventDao {

    void create(SchoolEvent event) throws DaoException;

    /**
     * Updates every editable field. Inside the same transaction it keeps the participants coherent:
     * if the event now has a competition, participants without position become "Participante"; if it
     * does not, their positions are cleared. It refuses (DaoException) to remove a competition that
     * already has podium places.
     */
    void update(SchoolEvent event) throws DaoException;

    Optional<SchoolEvent> findById(int id) throws DaoException;

    /** Every event, ordered by date (the calendar order is decided by the controller). */
    List<SchoolEvent> findAll() throws DaoException;

    /** Case-insensitive search in the name, description, place and organizer. */
    List<SchoolEvent> search(String term) throws DaoException;

    /** True when another event has the same name on the same date (excludeId = the event being edited, or null). */
    boolean nameAndDateExist(String name, LocalDate date, Integer excludeId) throws DaoException;

    int countParticipants(int eventId) throws DaoException;

    /** Event id -> number of participants, for every event with at least one. */
    Map<Integer, Integer> countParticipantsByEvent() throws DaoException;
}
