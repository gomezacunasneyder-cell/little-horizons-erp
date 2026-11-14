package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.enums.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence of the daily attendance (table asistencias). */
public interface AttendanceDao {

    void create(AttendanceRecord record) throws DaoException;

    /** Saves the state, observations and teacher of an existing record. */
    void update(AttendanceRecord record) throws DaoException;

    Optional<AttendanceRecord> find(int studentId, int subjectId, LocalDate date) throws DaoException;

    /** Everything registered for one subject on one day (all its groups). */
    List<AttendanceRecord> findBySubjectAndDate(int subjectId, LocalDate date) throws DaoException;

    /** What was registered for a student on one day, in every subject. */
    List<AttendanceRecord> findByStudentAndDate(int studentId, LocalDate date) throws DaoException;

    /** Attendance of a student between two dates (both included), oldest first. */
    List<AttendanceRecord> findByStudentBetween(int studentId, LocalDate from, LocalDate to) throws DaoException;

    /** Every record with that state between two dates, for the absence reports. */
    List<AttendanceRecord> findByStatusBetween(AttendanceStatus status, LocalDate from, LocalDate to) throws DaoException;
}
