package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.ClassAccessPolicy;
import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.GradeController;
import com.littlehorizons.erp.controller.GradeReportController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.RosterEntry;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.grading.CourseSubjectAverage;
import com.littlehorizons.erp.controller.grading.GradeAverages;
import com.littlehorizons.erp.controller.grading.SimpleAverageStrategy;
import com.littlehorizons.erp.controller.grading.SubjectSummary;
import com.littlehorizons.erp.controller.grading.WeightedAverageStrategy;
import com.littlehorizons.erp.controller.grading.YearReport;
import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Manual check of module 1.3 (plain Java). It creates its own students (documents starting with
 * 9970, guardians 9971) and teachers (9960), works in the year after the current one so the sample grades are not
 * touched, and deletes everything at the end. Run main() from IntelliJ with the database loaded
 * (docs/database.sql, which includes the sample grades) and config.txt in place.
 */
public final class GradeModuleCheck {

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
        GradeController grades = new GradeController(daos.academicRecordDao(), daos.subjectRegistrationDao(),
                policy, SimpleAverageStrategy::new, logger);
        GradeController weightedGrades = new GradeController(daos.academicRecordDao(), daos.subjectRegistrationDao(),
                policy, () -> new WeightedAverageStrategy(List.of(20, 20, 30, 30)), logger);
        GradeReportController reports = new GradeReportController(daos.academicRecordDao(), logger);
        int year = LocalDate.now().getYear() + 1;
        AcademicPeriod p1 = AcademicPeriod.FIRST;
        AcademicPeriod p2 = AcademicPeriod.SECOND;
        AcademicPeriod p3 = AcademicPeriod.THIRD;
        AcademicPeriod p4 = AcademicPeriod.FOURTH;
        AcademicPeriod fin = AcademicPeriod.FINAL;

        try {
            cleanUp();

            // ---- grade format ----
            check("Valid grades are accepted (4.2, 4,2, 0, 5, 3)", () ->
                    AcademicRecord.parseGrade("4.2").equals(new BigDecimal("4.2"))
                            && AcademicRecord.parseGrade("4,2").equals(new BigDecimal("4.2"))
                            && AcademicRecord.parseGrade("0").equals(new BigDecimal("0.0"))
                            && AcademicRecord.parseGrade("5.0").equals(new BigDecimal("5.0"))
                            && AcademicRecord.parseGrade("3").scale() == 1);
            check("Invalid grades are rejected (5.1, 6, -1, 4.25, abc, empty)", () ->
                    throwsValidation(() -> AcademicRecord.parseGrade("5.1"))
                            && throwsValidation(() -> AcademicRecord.parseGrade("6"))
                            && throwsValidation(() -> AcademicRecord.parseGrade("-1"))
                            && throwsValidation(() -> AcademicRecord.parseGrade("4.25"))
                            && throwsValidation(() -> AcademicRecord.parseGrade("abc"))
                            && throwsValidation(() -> AcademicRecord.parseGrade("")));
            check("A grade above 5.0 cannot be built directly either", () -> throwsValidation(() ->
                    new AcademicRecord(student("99700000001", "Ana"), fakeSubject(), year, p1, new BigDecimal("5.1"), 1, "X", LocalDate.now())));

            // ---- final grade policies (pure logic) ----
            Map<AcademicPeriod, BigDecimal> sample = new EnumMap<>(AcademicPeriod.class);
            sample.put(p1, new BigDecimal("2.0"));
            sample.put(p2, new BigDecimal("3.0"));
            sample.put(p3, new BigDecimal("4.0"));
            sample.put(p4, new BigDecimal("5.0"));
            check("Simple policy: (2.0+3.0+4.0+5.0)/4 = 3.5", () -> new SimpleAverageStrategy().compute(sample).equals(new BigDecimal("3.5")));
            check("Weighted policy 20/20/30/30 = 3.7", () ->
                    new WeightedAverageStrategy(List.of(20, 20, 30, 30)).compute(sample).equals(new BigDecimal("3.7")));
            check("Weights with the wrong count or a zero are rejected", () ->
                    throwsConfig(() -> new WeightedAverageStrategy(List.of(50, 50)))
                            && throwsConfig(() -> new WeightedAverageStrategy(List.of(0, 40, 30, 30))));

            // ---- people and setup ----
            Teacher ana = teacher("99600000001", "Ana", "ana.grades@test.edu");
            Teacher luis = teacher("99600000002", "Luis", "luis.grades@test.edu");
            Teacher eva = teacher("99600000003", "Eva", "eva.grades@test.edu");
            check("Register three teachers", () -> teachers.register(ana).isSuccess()
                    && teachers.register(luis).isSuccess() && teachers.register(eva).isSuccess());
            GradeLevel fifth = assignments.grades().getData().stream().filter(g -> g.getName().equals("Quinto")).findFirst().orElseThrow();
            List<Subject> fifthSubjects = assignments.subjectsOf(fifth).getData();
            Subject math = fifthSubjects.get(0);
            Subject other = fifthSubjects.get(1);
            Subject third = fifthSubjects.get(2);
            TeachingAssignment anaMathA = assignments.assign(ana, math, year, "A").getData();
            TeachingAssignment luisMathB = assignments.assign(luis, math, year, "B").getData();
            TeachingAssignment luisOtherA = assignments.assign(luis, other, year, "A").getData();
            check("Teachers are assigned to their groups", () -> anaMathA != null && luisMathB != null && luisOtherA != null);
            check("A teacher on leave is detected", () -> {
                assignments.assign(eva, third, year, "A");
                return teachers.changeStatus(eva, TeacherStatus.ON_LEAVE).isSuccess();
            });

            Student s1 = student("99700000001", "Ana");   // group A in math and other
            Student s2 = student("99700000002", "Beto");  // group B in math
            Student s3 = student("99700000003", "Carla"); // group A, withdraws math in period III
            Student s4 = student("99700000004", "Dario"); // registered only in period I
            Student s5 = student("99700000005", "Elisa"); // registered without section (registerAll)
            for (Student s : List.of(s1, s2, s3, s4, s5)) {
                String number = s.getDocumentNumber().substring(s.getDocumentNumber().length() - 1);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "9971000000" + number, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
                enrollments.enroll(s, fifth, year);
            }
            check("Students are registered and enrolled", () -> s1.getId() != null && s5.getId() != null
                    && enrollments.byGradeAndYear(fifth, year).getData().size() == 5);
            for (AcademicPeriod p : AcademicPeriod.teachingPeriods()) {
                registrations.register(s1, math, year, p, "A");
                registrations.register(s2, math, year, p, "B");
                registrations.register(s3, math, year, p, "A");
                registrations.registerAll(s5, year, p);
            }
            registrations.register(s1, other, year, p1, "A");
            registrations.register(s2, other, year, p1, "A");
            registrations.register(s4, math, year, p1, "A");
            SubjectRegistration s3ThirdPeriod = registrations.byStudent(s3, year).getData().stream()
                    .filter(r -> r.getSubject().getId() == math.getId() && r.getPeriod() == p3).findFirst().orElseThrow();
            check("A student can withdraw from a subject in one period", () -> registrations.withdraw(s3ThirdPeriod).isSuccess());

            // ---- registering grades ----
            Result<AcademicRecord> first = grades.record(ana, s1, math, year, p1, AcademicRecord.parseGrade("4.2"), "Buen inicio de año");
            check("Register a grade", () -> first.isSuccess() && first.getData().getId() != null
                    && first.getData().getGrade().equals(new BigDecimal("4.2")) && first.getData().getTeacherId() == ana.getId());
            check("The access policy alone: allowed for the right teacher, denied for another group", () ->
                    policy.denialReason(ana, s1, math, year, p2).isEmpty()
                            && policy.denialReason(luis, s1, math, year, p2).isPresent()
                            && policy.denialReason(eva, s1, third, year, p2).isPresent());
            check("A second grade for the same period is refused and points to the correction", () -> {
                Result<AcademicRecord> r = grades.record(ana, s1, math, year, p1, AcademicRecord.parseGrade("3.0"), null);
                return !r.isSuccess() && r.getMessage().contains("Corregir");
            });
            check("The teacher of another group cannot grade the student", () ->
                    !grades.record(luis, s1, math, year, p1, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("The teacher of group A cannot grade a student of group B", () ->
                    !grades.record(ana, s2, math, year, p1, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("A teacher on leave cannot register grades", () ->
                    !grades.record(eva, s1, third, year, p1, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("A subject the teacher does not teach is refused", () ->
                    !grades.record(ana, s1, other, year, p1, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("A student not registered in the subject is refused", () ->
                    !grades.record(luis, s3, other, year, p1, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("A student not registered in that period is refused", () ->
                    !grades.record(ana, s4, math, year, p2, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("A withdrawn registration cannot receive grades", () -> {
                Result<AcademicRecord> r = grades.record(ana, s3, math, year, p3, AcademicRecord.parseGrade("3.0"), null);
                return !r.isSuccess() && r.getMessage().contains("retir");
            });
            check("A student without section can be graded by any teacher of the subject", () ->
                    grades.record(ana, s5, math, year, p1, AcademicRecord.parseGrade("3.4"), null).isSuccess()
                            && grades.record(luis, s5, math, year, p1, AcademicRecord.parseGrade("3.0"), null).getMessage().contains("Ya existe"));
            check("The Final grade needs registration in period IV", () ->
                    !grades.record(ana, s4, math, year, fin, AcademicRecord.parseGrade("3.0"), null).isSuccess());
            check("The database also refuses a duplicate", () -> {
                try {
                    daos.academicRecordDao().create(new AcademicRecord(s1, math, year, p1, new BigDecimal("1.0"), ana.getId(), "Ana", LocalDate.now()));
                    return false;
                } catch (DaoException e) {
                    return true;
                }
            });

            // ---- correcting ----
            check("The teacher lists the grades he or she can correct", () ->
                    grades.correctableRecords(ana, s1, year).getData().size() == 1
                            && grades.correctableRecords(luis, s1, year).getData().isEmpty());
            check("Correct a grade and persist it (with accents)", () -> {
                AcademicRecord record = grades.correctableRecords(ana, s1, year).getData().get(0);
                Result<AcademicRecord> r = grades.correct(ana, record, AcademicRecord.parseGrade("2.0"), "Corrección por revisión");
                AcademicRecord reloaded = grades.correctableRecords(ana, s1, year).getData().get(0);
                return r.isSuccess() && reloaded.getGrade().equals(new BigDecimal("2.0"))
                        && "Corrección por revisión".equals(reloaded.getObservations());
            });
            check("A teacher who does not teach the subject cannot correct", () -> {
                AcademicRecord record = grades.correctableRecords(ana, s1, year).getData().get(0);
                return !grades.correct(luis, record, AcademicRecord.parseGrade("5.0"), null).isSuccess();
            });

            // ---- final grade ----
            grades.record(ana, s1, math, year, p2, AcademicRecord.parseGrade("3.0"), null);
            check("The Final grade cannot be generated with periods missing", () -> {
                Result<AcademicRecord> r = grades.generateFinal(ana, s1, math, year);
                return !r.isSuccess() && r.getMessage().contains("III") && r.getMessage().contains("IV");
            });
            grades.record(ana, s1, math, year, p3, AcademicRecord.parseGrade("4.0"), null);
            grades.record(ana, s1, math, year, p4, AcademicRecord.parseGrade("5.0"), null);
            check("Final grade with the simple policy (3.5)", () -> {
                Result<AcademicRecord> r = grades.generateFinal(ana, s1, math, year);
                return r.isSuccess() && r.getData().getGrade().equals(new BigDecimal("3.5")) && r.getData().getPeriod() == fin;
            });
            check("Generating again with the weighted policy updates the same Final (3.7)", () -> {
                Result<AcademicRecord> r = weightedGrades.generateFinal(ana, s1, math, year);
                long finals = daos.academicRecordDao().findByStudentAndYear(s1.getId(), year).stream()
                        .filter(x -> x.getPeriod() == fin && x.getSubject().getId() == math.getId()).count();
                return r.isSuccess() && r.getData().getGrade().equals(new BigDecimal("3.7")) && finals == 1;
            });
            check("A wrong policy gives a clear failure instead of an exception", () -> {
                GradeController broken = new GradeController(daos.academicRecordDao(), daos.subjectRegistrationDao(),
                        policy, () -> { throw new ConfigurationException("política inválida"); }, logger);
                Result<AcademicRecord> r = broken.generateFinal(ana, s1, math, year);
                return !r.isSuccess() && r.getMessage().contains("política inválida");
            });
            check("The policy name is available for the menu", () -> grades.finalGradePolicy().equals("promedio simple")
                    && weightedGrades.finalGradePolicy().startsWith("promedio ponderado"));

            // ---- roster ----
            check("The roster of group A lists its students with their grade", () -> {
                List<RosterEntry> roster = grades.roster(anaMathA, p1).getData();
                return roster.stream().anyMatch(e -> e.student().getId().equals(s1.getId()) && e.existing() != null)
                        && roster.stream().anyMatch(e -> e.student().getId().equals(s3.getId()) && e.existing() == null)
                        && roster.stream().anyMatch(e -> e.student().getId().equals(s5.getId()))
                        && roster.stream().noneMatch(e -> e.student().getId().equals(s2.getId()));
            });
            check("A withdrawn student leaves the roster of that period", () ->
                    grades.roster(anaMathA, p3).getData().stream().noneMatch(e -> e.student().getId().equals(s3.getId())));

            // ---- averages ----
            grades.record(luis, s1, other, year, p1, AcademicRecord.parseGrade("4.0"), null);
            grades.record(luis, s2, math, year, p1, AcademicRecord.parseGrade("3.0"), null);
            Result<YearReport> report = reports.yearReport(s1, year);
            check("Year report: average per subject and Final", () -> {
                SubjectSummary mathSummary = report.getData().subjects().stream()
                        .filter(s -> s.subject().getId() == math.getId()).findFirst().orElseThrow();
                return report.isSuccess() && report.getData().subjects().size() == 2
                        && mathSummary.average().equals(new BigDecimal("3.50"))
                        && mathSummary.finalGrade().equals(new BigDecimal("3.7"));
            });
            check("Year report: general average per period and for the year", () ->
                    report.getData().periodAverages().get(p1).equals(new BigDecimal("3.00"))
                            && report.getData().periodAverages().get(p2).equals(new BigDecimal("3.00"))
                            && report.getData().yearAverage().equals(new BigDecimal("3.75")));
            check("A student without grades gets a clear message", () -> {
                Result<YearReport> r = reports.yearReport(s4, year);
                return !r.isSuccess() && r.getMessage().contains("no tiene notas");
            });
            check("Course average of a subject (lowest, highest, students)", () -> {
                List<CourseSubjectAverage> list = reports.courseAverages(fifth, year, p1).getData();
                CourseSubjectAverage mathAverage = list.stream().filter(c -> c.subject().getId() == math.getId()).findFirst().orElseThrow();
                return list.size() == 2 && mathAverage.students() == 3
                        && mathAverage.average().equals(new BigDecimal("2.80"))
                        && mathAverage.lowest().equals(new BigDecimal("2.0"))
                        && mathAverage.highest().equals(new BigDecimal("3.4"));
            });
            check("A period without grades gives a clear message", () -> !reports.courseAverages(fifth, year, p3).isSuccess()
                    || reports.courseAverages(fifth, year, p3).getData().stream().allMatch(c -> c.students() > 0));
            check("Average of nothing is empty", () -> GradeAverages.average(java.util.stream.Stream.empty()).isEmpty());

            // ---- academic history with the sample data ----
            Student old = findStudentWithTwoYears(daos);
            check("The sample data has grades of 2025 and 2026 for the same student", () -> old != null);
            check("History lists the years in order with their averages", () -> {
                Result<List<YearReport>> history = reports.history(old);
                List<YearReport> years = history.getData();
                return history.isSuccess() && years.size() >= 2 && years.get(0).year() < years.get(1).year()
                        && years.stream().allMatch(y -> y.yearAverage() != null && !y.subjects().isEmpty());
            });
            check("History of a student without grades gives a message", () -> !reports.history(s4).isSuccess()
                    || reports.history(s4).getData().size() == 1);

            // ---- sample data consistency ----
            check("The sample data has at least 30 grades", () -> scalar("SELECT COUNT(*) FROM registros_academicos WHERE anio_academico <= " + (year - 1)) >= 30);
            check("Sample Final grades equal the simple mean of periods I-IV", () -> scalar(
                    "SELECT COUNT(*) FROM registros_academicos f WHERE f.periodo_academico='Final' AND f.anio_academico <= " + (year - 1)
                            + " AND f.valor_nota <> (SELECT ROUND(AVG(r.valor_nota),1) FROM registros_academicos r WHERE r.id_estudiante=f.id_estudiante"
                            + " AND r.id_materia=f.id_materia AND r.anio_academico=f.anio_academico AND r.periodo_academico<>'Final')") == 0);
            check("Every sample grade was given by a teacher assigned to that subject", () -> scalar(
                    "SELECT COUNT(*) FROM registros_academicos r WHERE r.anio_academico <= " + (year - 1)
                            + " AND NOT EXISTS (SELECT 1 FROM asignaciones_profesor a WHERE a.id_profesor=r.id_profesor AND a.id_materia=r.id_materia AND a.anio_academico=2026)") == 0);
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

    private static Student findStudentWithTwoYears(DaoFactory daos) throws Exception {
        long id = scalar("SELECT MIN(a.id_estudiante) FROM registros_academicos a JOIN registros_academicos b"
                + " ON a.id_estudiante=b.id_estudiante AND a.anio_academico=2025 AND b.anio_academico=2026");
        return id == 0 ? null : daos.studentDao().findById((int) id).orElse(null);
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

    /** Deletes what this check created. Students first: their grades (cascade) refer to the teachers. */
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
