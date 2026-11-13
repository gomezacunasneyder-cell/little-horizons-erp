package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnrollmentDao {

    void create(Enrollment enrollment) throws DaoException;

    Optional<Enrollment> findByStudentAndYear(int studentId, int academicYear) throws DaoException;

    List<Enrollment> findByStudent(int studentId) throws DaoException;

    /** Enrollments of a grade in a year, ordered by student name. */
    List<Enrollment> findByGradeAndYear(int gradeId, int academicYear) throws DaoException;

    void updateStatus(int enrollmentId, EnrollmentStatus status, LocalDate withdrawalDate) throws DaoException;
}
