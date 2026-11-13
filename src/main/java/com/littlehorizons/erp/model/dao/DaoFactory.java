package com.littlehorizons.erp.model.dao;

/**
 * FACTORY pattern: the controllers ask this factory for the DAOs they need and never write
 * "new JdbcStudentDao()". If tomorrow the data came from another technology, only the factory
 * implementation would change (Dependency Inversion: controllers depend on interfaces).
 */
public interface DaoFactory {

    StudentDao studentDao();

    GuardianDao guardianDao();

    GradeLevelDao gradeLevelDao();

    EnrollmentDao enrollmentDao();

    SubjectDao subjectDao();

    SubjectRegistrationDao subjectRegistrationDao();
}
