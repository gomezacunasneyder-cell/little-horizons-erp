package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.model.dao.*;

/** JDBC implementation of the factory: every DAO shares the same DatabaseConnection. */
public final class JdbcDaoFactory implements DaoFactory {

    private final DatabaseConnection database;

    public JdbcDaoFactory(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public StudentDao studentDao() {
        return new JdbcStudentDao(database);
    }

    @Override
    public GuardianDao guardianDao() {
        return new JdbcGuardianDao(database);
    }

    @Override
    public GradeLevelDao gradeLevelDao() {
        return new JdbcGradeLevelDao(database);
    }

    @Override
    public EnrollmentDao enrollmentDao() {
        return new JdbcEnrollmentDao(database);
    }

    @Override
    public SubjectDao subjectDao() {
        return new JdbcSubjectDao(database);
    }

    @Override
    public SubjectRegistrationDao subjectRegistrationDao() {
        return new JdbcSubjectRegistrationDao(database);
    }
}
