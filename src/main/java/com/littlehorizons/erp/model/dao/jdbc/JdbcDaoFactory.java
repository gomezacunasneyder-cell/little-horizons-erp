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

    @Override
    public TeacherDao teacherDao() {
        return new JdbcTeacherDao(database);
    }

    @Override
    public TeachingAssignmentDao teachingAssignmentDao() {
        return new JdbcTeachingAssignmentDao(database);
    }

    @Override
    public ScheduleDao scheduleDao() {
        return new JdbcScheduleDao(database);
    }

    @Override
    public AcademicRecordDao academicRecordDao() {
        return new JdbcAcademicRecordDao(database);
    }

    @Override
    public AttendanceDao attendanceDao() {
        return new JdbcAttendanceDao(database);
    }

    @Override
    public ClubDao clubDao() {
        return new JdbcClubDao(database);
    }

    @Override
    public ClubMembershipDao clubMembershipDao() {
        return new JdbcClubMembershipDao(database);
    }

    @Override
    public EventDao eventDao() {
        return new JdbcEventDao(database);
    }

    @Override
    public EventParticipationDao eventParticipationDao() {
        return new JdbcEventParticipationDao(database);
    }

    @Override
    public PaymentConceptDao paymentConceptDao() {
        return new JdbcPaymentConceptDao(database);
    }

    @Override
    public StudentConceptPriceDao studentConceptPriceDao() {
        return new JdbcStudentConceptPriceDao(database);
    }

    @Override
    public PaymentDao paymentDao() {
        return new JdbcPaymentDao(database);
    }

    @Override
    public InvoiceDao invoiceDao() {
        return new JdbcInvoiceDao(database);
    }

    @Override
    public ResourceDao resourceDao() {
        return new JdbcResourceDao(database);
    }

    @Override
    public LoanDao loanDao() {
        return new JdbcLoanDao(database);
    }
}
