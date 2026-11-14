package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.AttendanceController;
import com.littlehorizons.erp.controller.AttendanceReportController;
import com.littlehorizons.erp.controller.AttendanceRosterEntry;
import com.littlehorizons.erp.controller.ClassAccessPolicy;
import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.attendance.AbsenceAlert;
import com.littlehorizons.erp.controller.attendance.AbsenceReport;
import com.littlehorizons.erp.controller.attendance.AttendanceCounts;
import com.littlehorizons.erp.controller.attendance.AttendanceSettings;
import com.littlehorizons.erp.controller.attendance.AttendanceSummary;
import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.AttendanceStatus;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static com.littlehorizons.erp.model.enums.AttendanceStatus.ABSENT;
import static com.littlehorizons.erp.model.enums.AttendanceStatus.LATE;
import static com.littlehorizons.erp.model.enums.AttendanceStatus.MEDICAL_EXCUSE;
import static com.littlehorizons.erp.model.enums.AttendanceStatus.PRESENT;

/**
 * Manual check of module 1.4 (plain Java). It creates its own students (documents starting with
 * 9970, guardians 9971) and teachers (9960), works in the PREVIOUS school year so the sample
 * attendance of the current year is not touched, and deletes everything at the end. The last
 * checks read the sample data of docs/database.sql. Run main() from IntelliJ with the database
 * loaded and config.txt in place.
 */
public final class AttendanceModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        EnrollmentController enrollments = new EnrollmentController(daos.enrollmentDao(), daos.gradeLevelDao(), logger);
        SubjectRegistrationController registrations = new SubjectRegistrationController(
                daos.subjectRegistrationDao(), daos.subjectDao(), daos.enrollmentDao(), logger);
        AssignmentController assignments = new AssignmentController(daos.teachingAssignmentDao(),
                daos.scheduleDao(), daos.subjectDao(), daos.gradeLevelDao(), logger);
        ClassAccessPolicy policy = new ClassAccessPolicy(daos.subjectRegistrationDao(), daos.teachingAssignmentDao());
        AttendanceController attendance = new AttendanceController(daos.attendanceDao(),
                daos.subjectRegistrationDao(), policy, logger);
        int[] limit = {3};
        AttendanceReportController reports = new AttendanceReportController(daos.attendanceDao(), () -> limit[0], logger);

        int year = LocalDate.now().getYear() - 1;
        LocalDate monday = LocalDate.of(year, 10, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);

        try {
            cleanUp();

            // ---- enum and calendar ----
            check("The state mirrors the database labels", () ->
                    AttendanceStatus.fromDbLabel("Excusa Médica") == MEDICAL_EXCUSE
                            && AttendanceStatus.values().length == 4 && ABSENT.getDbLabel().equals("Ausente"));
            check("Only «Ausente» counts as an unexcused absence; present and late were in class", () ->
                    ABSENT.isUnexcusedAbsence() && !MEDICAL_EXCUSE.isUnexcusedAbsence() && !LATE.isUnexcusedAbsence()
                            && PRESENT.wasInClass() && LATE.wasInClass() && !ABSENT.wasInClass() && !MEDICAL_EXCUSE.wasInClass());
            check("A date belongs to a period by quarter", () ->
                    AcademicPeriod.ofDate(LocalDate.of(2026, 1, 1)) == AcademicPeriod.FIRST
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 3, 31)) == AcademicPeriod.FIRST
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 4, 1)) == AcademicPeriod.SECOND
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 6, 30)) == AcademicPeriod.SECOND
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 7, 1)) == AcademicPeriod.THIRD
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 9, 30)) == AcademicPeriod.THIRD
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 10, 1)) == AcademicPeriod.FOURTH
                            && AcademicPeriod.ofDate(LocalDate.of(2026, 12, 31)) == AcademicPeriod.FOURTH);

            // ---- entity validation ----
            Student probe = student("99700000009", "Prueba");
            Subject fake = fakeSubject();
            check("A Saturday or Sunday is not a school day", () ->
                    throwsValidation(() -> new AttendanceRecord(probe, fake, monday.minusDays(2), PRESENT, 1, "X"))
                            && throwsValidation(() -> new AttendanceRecord(probe, fake, monday.minusDays(1), PRESENT, 1, "X")));
            check("A future date is rejected", () -> throwsValidation(() -> AttendanceRecord.checkSchoolDay(LocalDate.now().plusDays(7))));
            check("Observations are trimmed, blank means none and more than 500 characters is rejected", () -> {
                AttendanceRecord r = new AttendanceRecord(probe, fake, monday, PRESENT, 1, "X");
                r.setObservations("  Incapacidad médica  ");
                boolean trimmed = "Incapacidad médica".equals(r.getObservations());
                r.setObservations("   ");
                return trimmed && r.getObservations() == null && throwsValidation(() -> r.setObservations("x".repeat(501)));
            });

            // ---- setup: teachers, students, assignments, registrations (period IV of the previous year) ----
            Teacher ana = teacher("99600000001", "Ana", "ana.att@test.edu");
            Teacher luis = teacher("99600000002", "Luis", "luis.att@test.edu");
            Teacher eva = teacher("99600000003", "Eva", "eva.att@test.edu");
            check("Register three teachers", () -> teachers.register(ana).isSuccess()
                    && teachers.register(luis).isSuccess() && teachers.register(eva).isSuccess());
            GradeLevel fifth = assignments.grades().getData().stream().filter(g -> g.getName().equals("Quinto")).findFirst().orElseThrow();
            List<Subject> fifthSubjects = assignments.subjectsOf(fifth).getData();
            Subject math = fifthSubjects.get(0);
            Subject other = fifthSubjects.get(1);
            Subject third = fifthSubjects.get(2);
            TeachingAssignment anaMathA = assignments.assign(ana, math, year, "A").getData();
            assignments.assign(luis, math, year, "B");
            assignments.assign(luis, other, year, "A");
            assignments.assign(eva, third, year, "A");
            teachers.changeStatus(eva, TeacherStatus.ON_LEAVE);

            Student s1 = student("99700000001", "Ana");   // group A in math and other
            Student s2 = student("99700000002", "Beto");  // group B in math
            Student s3 = student("99700000003", "Carla"); // group A, withdraws math in period IV
            Student s4 = student("99700000004", "Dario"); // enrolled but registered in no subject
            Student s5 = student("99700000005", "Elisa"); // registered without section (registerAll)
            for (Student s : List.of(s1, s2, s3, s4, s5)) {
                String number = s.getDocumentNumber().substring(s.getDocumentNumber().length() - 1);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "9971000000" + number, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
                enrollments.enroll(s, fifth, year);
            }
            AcademicPeriod p4 = AcademicPeriod.FOURTH;
            registrations.register(s1, math, year, p4, "A");
            registrations.register(s1, other, year, p4, "A");
            registrations.register(s2, math, year, p4, "B");
            registrations.register(s3, math, year, p4, "A");
            registrations.registerAll(s5, year, p4);
            SubjectRegistration s3Math = registrations.byStudent(s3, year).getData().stream()
                    .filter(r -> r.getSubject().getId() == math.getId()).findFirst().orElseThrow();
            check("Students are registered, enrolled and in their subjects", () -> s1.getId() != null && s5.getId() != null
                    && registrations.withdraw(s3Math).isSuccess());

            // ---- registering ----
            Result<AttendanceRecord> first = attendance.record(ana, s1, math, monday, PRESENT, null);
            check("Register attendance", () -> first.isSuccess() && first.getData().getId() != null
                    && daos.attendanceDao().find(s1.getId(), math.getId(), monday).orElseThrow().getStatus() == PRESENT);
            check("A second record the same day is refused and points to the correction", () -> {
                Result<AttendanceRecord> r = attendance.record(ana, s1, math, monday, ABSENT, null);
                return !r.isSuccess() && r.getMessage().contains("Corregir");
            });
            check("The database also refuses a duplicate", () -> {
                try {
                    daos.attendanceDao().create(new AttendanceRecord(s1, math, monday, ABSENT, ana.getId(), "Ana"));
                    return false;
                } catch (DaoException e) {
                    return true;
                }
            });
            check("A weekend and a future date give a message, not an exception", () ->
                    !attendance.record(ana, s1, math, monday.minusDays(1), PRESENT, null).isSuccess()
                            && !attendance.record(ana, s1, math, LocalDate.now().plusDays(7), PRESENT, null).isSuccess());
            check("The teacher of another group cannot mark the student", () ->
                    !attendance.record(luis, s1, math, monday.plusDays(1), PRESENT, null).isSuccess());
            check("The teacher of group A cannot mark a student of group B", () ->
                    !attendance.record(ana, s2, math, monday, PRESENT, null).isSuccess());
            check("A teacher on leave cannot register attendance", () ->
                    !attendance.record(eva, s1, third, monday, PRESENT, null).isSuccess());
            check("A subject the teacher does not teach is refused", () ->
                    !attendance.record(ana, s1, other, monday, PRESENT, null).isSuccess());
            check("A student not registered in any subject is refused", () ->
                    !attendance.record(ana, s4, math, monday, PRESENT, null).isSuccess());
            check("A period in which the student is not registered is refused", () ->
                    !attendance.record(ana, s1, math, LocalDate.of(year, 2, 2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)), PRESENT, null).isSuccess());
            check("A withdrawn registration cannot receive attendance", () -> {
                Result<AttendanceRecord> r = attendance.record(ana, s3, math, monday, PRESENT, null);
                return !r.isSuccess() && r.getMessage().contains("retir");
            });
            check("A student without section can be marked by the teacher of the subject", () ->
                    attendance.record(ana, s5, math, monday, PRESENT, null).isSuccess());

            // ---- roster ----
            check("The roster of group A lists its students with what is already registered", () -> {
                List<AttendanceRosterEntry> roster = attendance.roster(anaMathA, monday).getData();
                return roster.stream().anyMatch(e -> e.student().getId().equals(s1.getId()) && e.existing() != null)
                        && roster.stream().anyMatch(e -> e.student().getId().equals(s5.getId()))
                        && roster.stream().noneMatch(e -> e.student().getId().equals(s2.getId()))
                        && roster.stream().noneMatch(e -> e.student().getId().equals(s3.getId()));
            });
            check("The roster refuses a date outside the year of the assignment or a weekend", () ->
                    !attendance.roster(anaMathA, LocalDate.of(year + 1, 1, 12)).isSuccess()
                            && !attendance.roster(anaMathA, monday.minusDays(1)).isSuccess());

            // ---- correcting ----
            check("The teacher lists the attendance he or she can correct", () ->
                    attendance.correctableRecords(ana, s1, monday).getData().size() == 1
                            && attendance.correctableRecords(luis, s1, monday).getData().isEmpty());
            check("Correct the state and persist it (with accents)", () -> {
                AttendanceRecord record = attendance.correctableRecords(ana, s1, monday).getData().get(0);
                Result<AttendanceRecord> r = attendance.correct(ana, record, LATE, "Llegó después del timbre");
                AttendanceRecord reloaded = attendance.correctableRecords(ana, s1, monday).getData().get(0);
                return r.isSuccess() && reloaded.getStatus() == LATE && "Llegó después del timbre".equals(reloaded.getObservations());
            });
            check("A teacher who does not teach the subject cannot correct", () -> {
                AttendanceRecord record = attendance.correctableRecords(ana, s1, monday).getData().get(0);
                return !attendance.correct(luis, record, ABSENT, null).isSuccess();
            });

            // ---- data for the reports: s1 absent Tue-Fri (4), s2 absent Tue-Thu (3), s5 excused Tue-Fri ----
            for (int d = 1; d <= 4; d++) {
                attendance.record(ana, s1, math, monday.plusDays(d), ABSENT, null);
                attendance.record(ana, s5, math, monday.plusDays(d), MEDICAL_EXCUSE, "Incapacidad médica");
            }
            for (int d = 1; d <= 3; d++) {
                attendance.record(luis, s2, math, monday.plusDays(d), ABSENT, null);
            }
            attendance.record(luis, s1, other, monday, PRESENT, null);

            // ---- summary ----
            Result<AttendanceSummary> summary = reports.summary(s1, year);
            check("Summary of a student: counts per subject and total", () -> {
                AttendanceCounts total = summary.getData().overall();
                return summary.isSuccess() && summary.getData().subjects().size() == 2
                        && total.present() == 1 && total.late() == 1 && total.absent() == 4 && total.excused() == 0 && total.total() == 6;
            });
            check("Attendance rate = (present + late) / classes", () ->
                    summary.getData().overall().ratePercent().toPlainString().equals("33.3"));
            check("Excused absences are not counted in the rate", () ->
                    new AttendanceCounts(8, 1, 1, 5).ratePercent().toPlainString().equals("90.0")
                            && new AttendanceCounts(0, 0, 0, 3).ratePercent() == null);
            check("A student without records gets a clear message", () -> {
                Result<AttendanceSummary> r = reports.summary(s4, year);
                return !r.isSuccess() && r.getMessage().contains("no tiene asistencia");
            });

            // ---- concerning patterns ----
            check("More than 3 absences in a week is reported (4); 3 is not; excused ones do not count", () -> {
                limit[0] = 3;
                AbsenceReport report = reports.concerningAbsences(monday, friday).getData();
                return report.alerts().size() == 1 && report.alerts().get(0).student().getId().equals(s1.getId())
                        && report.alerts().get(0).count() == 4 && report.alerts().get(0).weekStart().equals(monday)
                        && report.weeklyLimit() == 3;
            });
            check("The limit is configurable: with 2 the student with 3 is also reported, worst first", () -> {
                limit[0] = 2;
                List<AbsenceAlert> alerts = reports.concerningAbsences(monday, friday).getData().alerts();
                return alerts.size() == 2 && alerts.get(0).count() == 4 && alerts.get(1).count() == 3
                        && alerts.get(1).student().getId().equals(s2.getId());
            });
            check("With a limit of 4 nobody is reported (the rule is «more than»)", () -> {
                limit[0] = 4;
                return reports.concerningAbsences(monday, friday).getData().alerts().isEmpty();
            });
            check("A report that cuts the week does not count absences outside the range", () -> {
                limit[0] = 3;
                return reports.concerningAbsences(monday, monday.plusDays(2)).getData().alerts().isEmpty();
            });
            check("Wrong ranges give a message", () ->
                    !reports.concerningAbsences(friday, monday).isSuccess()
                            && !reports.concerningAbsences(monday.minusYears(2), monday).isSuccess());
            check("A wrong limit in config.txt gives a message instead of an exception", () -> {
                AttendanceReportController broken = new AttendanceReportController(daos.attendanceDao(),
                        () -> { throw new ConfigurationException("límite inválido"); }, logger);
                Result<AbsenceReport> r = broken.concerningAbsences(monday, friday);
                return !r.isSuccess() && r.getMessage().contains("límite inválido");
            });
            check("config.txt limit: «3» and « 5 » are valid; 0, 31, abc and empty are not", () ->
                    AttendanceSettings.parseLimit("3") == 3 && AttendanceSettings.parseLimit(" 5 ") == 5
                            && throwsConfig(() -> AttendanceSettings.parseLimit("0"))
                            && throwsConfig(() -> AttendanceSettings.parseLimit("31"))
                            && throwsConfig(() -> AttendanceSettings.parseLimit("abc"))
                            && throwsConfig(() -> AttendanceSettings.parseLimit("")));

            // ---- sample data of docs/database.sql ----
            check("The sample data has at least 30 attendance records and the four states", () ->
                    scalar("SELECT COUNT(*) FROM asistencias WHERE YEAR(fecha_asistencia) = 2026") >= 30
                            && scalar("SELECT COUNT(DISTINCT estado_asistencia) FROM asistencias WHERE YEAR(fecha_asistencia) = 2026") == 4);
            check("Sample data: every record was registered by a teacher assigned to that subject", () -> scalar(
                    "SELECT COUNT(*) FROM asistencias a WHERE YEAR(a.fecha_asistencia) = 2026 AND NOT EXISTS (SELECT 1 FROM asignaciones_profesor p "
                            + "WHERE p.id_profesor = a.registrado_por AND p.id_materia = a.id_materia AND p.anio_academico = 2026)") == 0);
            check("Sample data: the report finds the 4 planted cases (6, 5, 4 and 4 absences) and not the 3-absence or excused ones", () -> {
                limit[0] = 3;
                List<AbsenceAlert> alerts = reports.concerningAbsences(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).getData().alerts();
                List<Integer> counts = alerts.stream().map(AbsenceAlert::count).toList();
                return alerts.size() == 4 && counts.equals(List.of(6, 5, 4, 4))
                        && alerts.get(0).student().getFullName().startsWith("David Arango")
                        && alerts.stream().noneMatch(a -> a.student().getFullName().startsWith("Thomas Cortés"));
            });
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Student student(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2015, 5, 20)).admissionDate(LocalDate.now()).build();
    }

    private static Teacher teacher(String document, String firstName, String email) {
        return new Teacher(DocumentType.CC, document, firstName, "Prueba", email, "Matemáticas", LocalDate.of(2020, 1, 1));
    }

    private static Subject fakeSubject() {
        return new Subject(1, "X-1", "Materia", new GradeLevel(1, "Primero", 1), 2, true);
    }

    private static long scalar(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static boolean throwsValidation(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ValidationException e) {
            return true;
        }
    }

    private static boolean throwsConfig(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ConfigurationException e) {
            return true;
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

    /** Deletes what this check created. Students first: their attendance (cascade) refers to the teachers. */
    private static void cleanUp() throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            execute(connection, "DELETE FROM estudiantes WHERE numero_documento LIKE '9970%'");
            execute(connection, "DELETE FROM acudientes WHERE numero_documento LIKE '9971%'");
            execute(connection, "DELETE FROM profesores WHERE numero_documento LIKE '9960%'");
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
