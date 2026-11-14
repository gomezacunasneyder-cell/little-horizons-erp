package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.ScheduleSlot;

import java.util.List;

public interface ScheduleDao {

    void create(ScheduleSlot slot) throws DaoException;

    void delete(int slotId) throws DaoException;

    List<ScheduleSlot> findByAssignment(int assignmentId) throws DaoException;

    /** Every class block a teacher has in a year, ordered by day and start time. */
    List<ScheduleSlot> findByTeacherAndYear(int teacherId, int academicYear) throws DaoException;

    /** Every class block of a group (grade + section) in a year, ordered by day and start time. */
    List<ScheduleSlot> findByGroup(int gradeId, String section, int academicYear) throws DaoException;
}
