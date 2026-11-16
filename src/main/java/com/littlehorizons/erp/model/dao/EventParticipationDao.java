package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.EventParticipation;

import java.util.List;

public interface EventParticipationDao {

    void create(EventParticipation participation) throws DaoException;

    /** Saves the position of an existing participation (null for events without competition). */
    void updatePosition(EventParticipation participation) throws DaoException;

    /** Removes a participation (a student registered by mistake). */
    void delete(EventParticipation participation) throws DaoException;

    boolean exists(int eventId, int studentId) throws DaoException;

    /** The participants of an event (the controller sorts them by position). */
    List<EventParticipation> findByEvent(int eventId) throws DaoException;

    /** Every event the student took part in, newest first. */
    List<EventParticipation> findByStudent(int studentId) throws DaoException;
}
