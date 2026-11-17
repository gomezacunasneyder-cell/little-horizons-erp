package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.DebtorController;
import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.LoanController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StartupController;
import com.littlehorizons.erp.controller.StatisticsController;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.report.OverviewAnalyzer;
import com.littlehorizons.erp.controller.report.SchoolOverview;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.MainMenuRequestedException;
import com.littlehorizons.erp.exception.OperationCancelledException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.ConsoleInput;
import com.littlehorizons.erp.view.ConsoleOutput;
import com.littlehorizons.erp.view.MainMenuView;
import com.littlehorizons.erp.view.MenuView;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual check of "Reportes y Estadísticas" and of the "inicio" shortcut (plain Java). It creates and
 * deletes nothing: every figure of the overview is compared with a direct SQL count, so a wrong filter
 * or a missing state would show up. (Listing the pending loans only refreshes the "Vencido" flag of the
 * loans already late on the fixed date, which the sample data already have.) The clock is fixed, so the
 * result does not depend on the day it runs. Run main() from IntelliJ with the database loaded and
 * config.txt in place.
 */
public final class ReportsModuleCheck {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        ClubController clubs = new ClubController(daos.clubDao(), logger);
        EventController events = new EventController(daos.eventDao(), logger);
        ResourceController resources = new ResourceController(daos.resourceDao(), logger);
        LoanController loans = new LoanController(daos.loanDao(), daos.resourceDao(), logger, () -> DAY);
        DebtorController debtors = new DebtorController(daos.enrollmentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.paymentDao(), daos.studentDao(), logger);
        StatisticsController statistics = new StatisticsController(students, teachers, daos.enrollmentDao(), clubs,
                events, resources, loans, debtors, logger, () -> DAY);

        // ---- the "inicio" keyword ----
        check("'inicio' (in any case, with spaces) throws MainMenuRequestedException, and it is not a cancel", () ->
                throwsHome("inicio") && throwsHome("INICIO") && throwsHome("  Inicio  ")
                        && !OperationCancelledException.class.isAssignableFrom(MainMenuRequestedException.class));
        check("'cancelar' still throws OperationCancelledException (not the home one); normal text is returned untouched", () -> {
            ConsoleInput cancel = input("cancelar\n");
            boolean cancelled;
            try {
                cancel.readLine("> ");
                cancelled = false;
            } catch (OperationCancelledException e) {
                cancelled = true;
            }
            return cancelled && "inicios y finales".equals(input("inicios y finales\n").readLine("> "))
                    && "7".equals(input("7\n").readLine("> "));
        });
        check("The keyword also works inside a question that validates (readInt), because it is checked before parsing", () -> {
            try {
                input("inicio\n").readInt("n: ", 1, 5);
                return false;
            } catch (MainMenuRequestedException e) {
                return true;
            }
        });
        check("The main menu catches it, says so, keeps running and still exits with option 8", () -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ConsoleOutput output = new ConsoleOutput(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            ConsoleInput in = new ConsoleInput(new Scanner("7\n7\n8\n"), output);
            AtomicInteger opened = new AtomicInteger();
            MenuView deepModule = () -> {
                if (opened.incrementAndGet() == 1) {
                    throw new MainMenuRequestedException(); // as if the user typed "inicio" ten screens down
                }
            };
            new MainMenuView(in, output, new StartupController(DatabaseConnection.getInstance(), ErrorLogger.getInstance()),
                    Map.of(7, deepModule)).start();
            String text = buffer.toString(StandardCharsets.UTF_8);
            return opened.get() == 2 && text.contains("Menú principal.") && text.contains("Hasta pronto.")
                    && text.contains("«inicio»");
        });

        // ---- the overview against direct SQL ----
        Result<SchoolOverview> result = statistics.overview();
        check("The overview is built and carries the date and the academic year of the clock", () ->
                result.isSuccess() && result.getData().asOf().equals(DAY) && result.getData().academicYear() == 2026);
        SchoolOverview overview = result.getData();

        check("Students: the total and each state match the database (every state present, 0 when none)", () ->
                overview.totalStudents() == count("SELECT COUNT(*) FROM estudiantes")
                        && overview.studentsByStatus().keySet().size() == StudentStatus.values().length
                        && overview.studentsByStatus().entrySet().stream().allMatch(e ->
                        e.getValue() == countWhere("estudiantes", "estado", e.getKey().getDbLabel())));
        check("Teachers: the total and each state match the database", () ->
                overview.totalTeachers() == count("SELECT COUNT(*) FROM profesores")
                        && overview.teachersByStatus().keySet().size() == TeacherStatus.values().length
                        && overview.teachersByStatus().entrySet().stream().allMatch(e ->
                        e.getValue() == countWhere("profesores", "estado", e.getKey().getDbLabel())));
        check("Enrollments: the active ones of the year match the database and the grades come from lowest to highest", () -> {
            long expected = count("SELECT COUNT(*) FROM matriculas WHERE anio_academico = 2026 AND estado_matricula = 'Activa'");
            List<Integer> levels = gradeLevelsInOrder(overview.enrolledByGrade().keySet());
            return overview.totalEnrolled() == expected && expected > 0
                    && levels.equals(levels.stream().sorted().toList()) && levels.size() == overview.enrolledByGrade().size()
                    && overview.enrolledByGrade().entrySet().stream().allMatch(e -> e.getValue() == count(
                    "SELECT COUNT(*) FROM matriculas m JOIN grados g ON g.id_grado = m.id_grado "
                            + "WHERE m.anio_academico = 2026 AND m.estado_matricula = 'Activa' AND g.nombre_grado = '" + e.getKey() + "'"));
        });
        check("The analyzer returns an empty map for no enrollments, and the same totals as the overview for the real ones", () -> {
            List<Enrollment> active = daos.enrollmentDao().findActiveByYear(2026);
            return OverviewAnalyzer.enrolledByGrade(List.of()).isEmpty()
                    && OverviewAnalyzer.enrolledByGrade(active).equals(overview.enrolledByGrade());
        });
        check("Clubs and events are exactly the summaries of their own modules", () ->
                overview.clubs().equals(clubs.catalog().getData().summary())
                        && overview.events().equals(events.calendar().getData().summary())
                        && overview.clubs().totalClubs() == count("SELECT COUNT(*) FROM clubes"));
        check("Resources: the total and each state match the database", () ->
                overview.totalResources() == count("SELECT COUNT(*) FROM recursos")
                        && overview.resourcesByStatus().keySet().size() == ResourceStatus.values().length
                        && overview.resourcesByStatus().entrySet().stream().allMatch(e ->
                        e.getValue() == countWhere("recursos", "estado_recurso", e.getKey().getDbLabel())));
        check("Loans: 10 pending and 5 overdue on 2026-11-16 (the sample data), the same as the loan module says", () ->
                overview.openLoans() == 10 && overview.overdueLoans() == 5
                        && overview.openLoans() == loans.pending().getData().total()
                        && overview.overdueLoans() == loans.overdueReport().getData().total()
                        && overview.overdueLoans() <= overview.openLoans());
        check("Finances: the debtors and the amount owed are those of the debtors report of the finance module", () -> {
            DebtorReport report = debtors.report(DAY).getData();
            return overview.debtorStudents() == report.debtors().size() && overview.debtorStudents() > 0
                    && overview.totalOwed().compareTo(report.totalPending()) == 0
                    && overview.totalOwed().compareTo(BigDecimal.ZERO) > 0;
        });
        check("A clock in another year changes the academic year and the debtors are calculated for that year", () -> {
            StatisticsController later = new StatisticsController(students, teachers, daos.enrollmentDao(), clubs, events,
                    resources, loans, debtors, logger, () -> LocalDate.of(2025, 11, 16));
            Result<SchoolOverview> other = later.overview();
            return other.isSuccess() && other.getData().academicYear() == 2025
                    && other.getData().totalEnrolled() == count(
                    "SELECT COUNT(*) FROM matriculas WHERE anio_academico = 2025 AND estado_matricula = 'Activa'");
        });

        // ---- failure of one module ----
        check("If a module fails, the overview fails with that module's own message instead of printing half of it", () -> {
            StudentDao broken = (StudentDao) Proxy.newProxyInstance(StudentDao.class.getClassLoader(),
                    new Class<?>[]{StudentDao.class}, (proxy, method, arguments) -> {
                        throw new DaoException("No se pudo leer a los estudiantes (simulado).");
                    });
            StatisticsController failing = new StatisticsController(new StudentController(broken, logger), teachers,
                    daos.enrollmentDao(), clubs, events, resources, loans, debtors, logger, () -> DAY);
            Result<SchoolOverview> failed = failing.overview();
            return !failed.isSuccess() && failed.getData() == null
                    && "No se pudo leer a los estudiantes (simulado).".equals(failed.getMessage());
        });

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static ConsoleInput input(String typed) {
        return new ConsoleInput(new Scanner(typed), new ConsoleOutput(new PrintStream(new ByteArrayOutputStream())));
    }

    private static boolean throwsHome(String typed) {
        try {
            input(typed + "\n").readLine("> ");
            return false;
        } catch (MainMenuRequestedException e) {
            return true;
        }
    }

    private static List<Integer> gradeLevelsInOrder(java.util.Collection<String> names) {
        List<Integer> levels = new java.util.ArrayList<>();
        for (String name : names) {
            levels.add((int) count("SELECT nivel_numerico FROM grados WHERE nombre_grado = '" + name + "'"));
        }
        return levels;
    }

    private static long countWhere(String table, String column, String value) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException | DaoException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long count(String sql) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        } catch (SQLException | DaoException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(String name, Callable<Boolean> test) {
        boolean passed;
        String detail = "";
        try {
            passed = test.call();
        } catch (Exception e) {
            passed = false;
            detail = " -> " + e;
        }
        System.out.println((passed ? "PASS  " : "FAIL  ") + name + detail);
        if (!passed) {
            failures++;
        }
    }
}
