package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;

import java.util.List;
import java.util.Optional;

public interface StudentConceptPriceDao {

    /** Saves the value; if the student already had one for that concept and year, it is replaced. */
    void save(StudentConceptPrice price) throws DaoException;

    /** Removes the personalized value; returns false when there was none. */
    boolean delete(Student student, PaymentConcept concept, int academicYear) throws DaoException;

    Optional<StudentConceptPrice> find(Student student, PaymentConcept concept, int academicYear) throws DaoException;

    /** Every personalized value of the student in the year (active or inactive concepts). */
    List<StudentConceptPrice> findByStudent(Student student, int academicYear) throws DaoException;
}
