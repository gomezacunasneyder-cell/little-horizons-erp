package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.TeachingAssignment;

import java.util.List;
import java.util.Optional;

public interface TeachingAssignmentDao {

    void create(TeachingAssignment assignment) throws DaoException;

    /** Deletes the assignment; its schedule slots disappear with it (ON DELETE CASCADE). */
    void delete(int assignmentId) throws DaoException;

    List<TeachingAssignment> findByTeacherAndYear(int teacherId, int academicYear) throws DaoException;

    List<TeachingAssignment> findBySubjectAndYear(int subjectId, int academicYear) throws DaoException;

    Optional<TeachingAssignment> findBySubjectYearAndSection(int subjectId, int academicYear, String section) throws DaoException;
}
