package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.util.List;
import java.util.Optional;

public interface AcademicRecordDao {

    void create(AcademicRecord record) throws DaoException;

    /** Saves the grade, observations, teacher and date of an existing record. */
    void update(AcademicRecord record) throws DaoException;

    Optional<AcademicRecord> find(int studentId, int subjectId, int academicYear, AcademicPeriod period) throws DaoException;

    /** Every grade of the student in every year, oldest first. */
    List<AcademicRecord> findByStudent(int studentId) throws DaoException;

    List<AcademicRecord> findByStudentAndYear(int studentId, int academicYear) throws DaoException;

    /** Every grade of the subjects of one grade in one year: the data of a whole course. */
    List<AcademicRecord> findByGradeAndYear(int gradeId, int academicYear) throws DaoException;

    List<AcademicRecord> findBySubjectYearAndPeriod(int subjectId, int academicYear, AcademicPeriod period) throws DaoException;
}
