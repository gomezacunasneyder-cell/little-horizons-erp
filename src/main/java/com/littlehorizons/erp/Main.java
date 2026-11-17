package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.AttendanceController;
import com.littlehorizons.erp.controller.AttendanceReportController;
import com.littlehorizons.erp.controller.ClassAccessPolicy;
import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.ClubMembershipController;
import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.EventParticipationController;
import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.InvoiceController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.finance.BillingProfile;
import com.littlehorizons.erp.controller.finance.InvoiceFileStore;
import com.littlehorizons.erp.controller.StudentPriceController;
import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.GradeController;
import com.littlehorizons.erp.controller.GradeReportController;
import com.littlehorizons.erp.controller.DebtorController;
import com.littlehorizons.erp.controller.GuardianController;
import com.littlehorizons.erp.controller.ScheduleController;
import com.littlehorizons.erp.controller.StartupController;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.attendance.AttendanceSettings;
import com.littlehorizons.erp.controller.grading.GradingStrategies;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.util.AppConfig;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.AcademicMenuView;
import com.littlehorizons.erp.view.AttendanceMenuView;
import com.littlehorizons.erp.view.ClubMenuView;
import com.littlehorizons.erp.view.ConceptMenuView;
import com.littlehorizons.erp.view.FinancialMenuView;
import com.littlehorizons.erp.view.InvoiceMenuView;
import com.littlehorizons.erp.view.PaymentMenuView;
import com.littlehorizons.erp.view.ResourceMenuView;
import com.littlehorizons.erp.view.ResourcesMenuView;
import com.littlehorizons.erp.view.EventMenuView;
import com.littlehorizons.erp.view.ExtracurricularMenuView;
import com.littlehorizons.erp.view.AssignmentMenuView;
import com.littlehorizons.erp.view.ConsoleInput;
import com.littlehorizons.erp.view.EnrollmentMenuView;
import com.littlehorizons.erp.view.GradeMenuView;
import com.littlehorizons.erp.view.GuardianMenuView;
import com.littlehorizons.erp.view.MenuView;
import com.littlehorizons.erp.view.StudentMenuView;
import com.littlehorizons.erp.view.SubjectRegistrationMenuView;
import com.littlehorizons.erp.view.TeacherMenuView;
import com.littlehorizons.erp.view.ConsoleOutput;
import com.littlehorizons.erp.view.MainMenuView;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entry point. It only builds the objects the program needs, hands them to the main menu and
 * lets it run. No logic lives here.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ConsoleOutput output = new ConsoleOutput(ConsoleOutput.utf8Stdout());
        ConsoleInput input = ConsoleInput.fromStandardInput(output);
        StartupController startup = new StartupController(DatabaseConnection.getInstance(), ErrorLogger.getInstance());

        // Dependency injection: everything is created here and handed down through constructors.
        ErrorLogger logger = ErrorLogger.getInstance();
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());

        StudentController students = new StudentController(daos.studentDao(), logger);
        GuardianController guardians = new GuardianController(daos.studentDao(), daos.guardianDao(), logger);
        EnrollmentController enrollments = new EnrollmentController(daos.enrollmentDao(), daos.gradeLevelDao(), logger);
        SubjectRegistrationController registrations = new SubjectRegistrationController(
                daos.subjectRegistrationDao(), daos.subjectDao(), daos.enrollmentDao(), logger);

        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        AssignmentController assignments = new AssignmentController(daos.teachingAssignmentDao(),
                daos.scheduleDao(), daos.subjectDao(), daos.gradeLevelDao(), logger);
        ScheduleController schedules = new ScheduleController(daos.scheduleDao(), logger);

        ClassAccessPolicy classAccess = new ClassAccessPolicy(daos.subjectRegistrationDao(), daos.teachingAssignmentDao());

        GradeController gradeController = new GradeController(daos.academicRecordDao(), daos.subjectRegistrationDao(),
                classAccess,
                () -> GradingStrategies.fromConfig(AppConfig.getInstance()), logger);
        GradeReportController gradeReports = new GradeReportController(daos.academicRecordDao(), logger);

        AttendanceController attendance = new AttendanceController(daos.attendanceDao(),
                daos.subjectRegistrationDao(), classAccess, logger);
        AttendanceReportController attendanceReports = new AttendanceReportController(daos.attendanceDao(),
                () -> AttendanceSettings.weeklyAbsenceLimit(AppConfig.getInstance()), logger);

        ClubController clubs = new ClubController(daos.clubDao(), logger);
        ClubMembershipController clubMemberships = new ClubMembershipController(
                daos.clubMembershipDao(), daos.clubDao(), logger);
        EventController events = new EventController(daos.eventDao(), logger);
        EventParticipationController eventParticipations = new EventParticipationController(
                daos.eventParticipationDao(), daos.eventDao(), logger);
        PaymentConceptController paymentConcepts = new PaymentConceptController(daos.paymentConceptDao(), logger);
        StudentPriceController studentPrices = new StudentPriceController(
                daos.studentConceptPriceDao(), daos.paymentConceptDao(), logger);

        MenuView studentModule = new StudentMenuView(input, output, students, guardians,
                new GuardianMenuView(input, output, students, guardians),
                new EnrollmentMenuView(input, output, enrollments, students),
                new SubjectRegistrationMenuView(input, output, registrations, students));

        MenuView teacherModule = new TeacherMenuView(input, output, teachers,
                new AssignmentMenuView(input, output, assignments, schedules, teachers));

        MenuView academicModule = new AcademicMenuView(input, output,
                new GradeMenuView(input, output, gradeController, gradeReports, assignments, teachers, students),
                new AttendanceMenuView(input, output, attendance, attendanceReports, assignments, teachers, students));

        MenuView extracurricularModule = new ExtracurricularMenuView(input, output,
                new ClubMenuView(input, output, clubs, clubMemberships, teachers, students),
                new EventMenuView(input, output, events, eventParticipations, teachers, students));

        PaymentController payments = new PaymentController(daos.paymentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.enrollmentDao(), daos.studentDao(), logger);
        DebtorController debtors = new DebtorController(daos.enrollmentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.paymentDao(), daos.studentDao(), logger);
        InvoiceController invoices = new InvoiceController(daos.invoiceDao(), daos.paymentDao(), daos.studentDao(),
                () -> BillingProfile.fromConfig(AppConfig.getInstance()),
                () -> new InvoiceFileStore(Path.of(AppConfig.getInstance()
                        .optional("billing.invoices.dir", InvoiceFileStore.DEFAULT_DIRECTORY))),
                LocalDateTime::now, logger);
        MenuView financeModule = new FinancialMenuView(input, output,
                new ConceptMenuView(input, output, paymentConcepts, studentPrices, students),
                new PaymentMenuView(input, output, payments, debtors, guardians, paymentConcepts, students, invoices),
                new InvoiceMenuView(input, output, invoices, guardians, students));

        ResourceController resources = new ResourceController(daos.resourceDao(), logger);
        MenuView resourceModule = new ResourcesMenuView(input, output, new ResourceMenuView(input, output, resources));

        new MainMenuView(input, output, startup,
                Map.of(1, studentModule, 2, academicModule, 3, teacherModule, 4, extracurricularModule,
                        5, financeModule, 6, resourceModule)).start();
    }
}
