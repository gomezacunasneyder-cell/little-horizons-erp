package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.report.OverviewAnalyzer;
import com.littlehorizons.erp.controller.report.SchoolOverview;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.EnumCounts;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Reportes y Estadísticas": puts together the general picture of the school. It does not repeat
 * any query or rule: it asks the controller of each module for its own figures (the club
 * catalog, the debtors, the overdue loans...) and only adds the totals that cross modules. That
 * way a number has ONE place where it is calculated, and the overview can never disagree with
 * the screen of the module.
 */
public class StatisticsController extends BaseController {

    private final StudentController students;
    private final TeacherController teachers;
    private final EnrollmentDao enrollmentDao;
    private final ClubController clubs;
    private final EventController events;
    private final ResourceController resources;
    private final LoanController loans;
    private final DebtorController debtors;
    private final Supplier<LocalDate> today;

    public StatisticsController(StudentController students, TeacherController teachers, EnrollmentDao enrollmentDao,
                                ClubController clubs, EventController events, ResourceController resources,
                                LoanController loans, DebtorController debtors, ErrorLogger logger,
                                Supplier<LocalDate> today) {
        super(logger);
        this.students = students;
        this.teachers = teachers;
        this.enrollmentDao = enrollmentDao;
        this.clubs = clubs;
        this.events = events;
        this.resources = resources;
        this.loans = loans;
        this.debtors = debtors;
        this.today = today;
    }

    public Result<SchoolOverview> overview() {
        return execute("Build the school overview", () -> {
            LocalDate asOf = today.get();
            int year = asOf.getYear();

            List<Student> allStudents = require(students.listAll());
            List<Teacher> allTeachers = require(teachers.listAll());
            List<Enrollment> enrollments = enrollmentDao.findActiveByYear(year);
            DebtorReport debt = require(debtors.report(asOf));

            return Result.ok(new SchoolOverview(asOf, year,
                    EnumCounts.countBy(StudentStatus.class, allStudents, Student::getStatus),
                    OverviewAnalyzer.enrolledByGrade(enrollments),
                    EnumCounts.countBy(TeacherStatus.class, allTeachers, Teacher::getStatus),
                    require(clubs.catalog()).summary(),
                    require(events.calendar()).summary(),
                    require(resources.inventory(null, null)).byStatus(),
                    require(loans.pending()).total(),
                    require(loans.overdueReport()).total(),
                    debt.debtors().size(),
                    debt.totalPending()), "");
        });
    }

    /**
     * The data of a module report, or a failure carrying the message that module already wrote
     * (and already logged, if it was technical), so the same error is not logged twice.
     */
    private static <T> T require(Result<T> result) {
        if (!result.isSuccess()) {
            throw new ValidationException(result.getMessage());
        }
        return result.getData();
    }
}
