package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;

import java.util.List;

public interface SubjectRegistrationDao {

    void create(SubjectRegistration registration) throws DaoException;

    boolean exists(int studentId, int subjectId, int academicYear, AcademicPeriod period) throws DaoException;

    List<SubjectRegistration> findByStudentAndYear(int studentId, int academicYear) throws DaoException;

    void updateStatus(int registrationId, RegistrationStatus status) throws DaoException;
}
