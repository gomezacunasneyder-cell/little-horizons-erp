package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Manual check of module 1.1 (plain Java). It creates its own test students (documents that
 * start with 9990) and deletes them at the end, so the real data is not modified. Run main()
 * from IntelliJ with the database loaded and config.txt in place.
 */
public final class StudentModuleCheck {

    private static final String TEST_PREFIX = "9990";
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), daos.guardianDao(), logger);
        EnrollmentController enrollments = new EnrollmentController(daos.enrollmentDao(), daos.gradeLevelDao(), logger);
        SubjectRegistrationController registrations = new SubjectRegistrationController(
                daos.subjectRegistrationDao(), daos.subjectDao(), daos.enrollmentDao(), logger);
        int year = LocalDate.now().getYear();

        try {
            cleanUp();

            // ---- entity validation ----
            check("Student with an invalid document is rejected", () -> throwsValidation(() -> student("12", "Ana")));
            check("Letters in a TI/CC/CE document are rejected", () -> throwsValidation(() -> student("ABC12345", "Ana"))
                    && throwsValidation(() -> com.littlehorizons.erp.model.entity.Person.checkDocumentNumber(DocumentType.CC, "12a45678")));
            check("Passport accepts letters and digits", () ->
                    "AB1234567".equals(com.littlehorizons.erp.model.entity.Person.checkDocumentNumber(DocumentType.PASSPORT, "AB1234567")));
            check("Student with digits in the name is rejected", () -> throwsValidation(() -> student("99900000001", "An4")));
            check("Student born in the future is rejected", () -> throwsValidation(() ->
                    baseBuilder("99900000001", "Ana").birthDate(LocalDate.now().plusDays(1)).build()));
            check("Guardian with TI document is rejected", () -> throwsValidation(() ->
                    new Guardian(DocumentType.TI, "99910000001", "Luis", "Pérez", "Padre", "3001234567")));
            check("Guardian occupation and relationship must be letters", () -> {
                Guardian g = new Guardian(DocumentType.CC, "99910000001", "Luis", "Pérez", "Padre", "3001234567");
                return throwsValidation(() -> g.setOccupation("372498632984"))
                        && throwsValidation(() -> g.setRelationship("123"))
                        && throwsValidation(() -> new Guardian(DocumentType.CC, "99910000001", "Luis", "Pérez", "T1o", "3001234567"));
            });
            check("Relationship must be one of the allowed options", () -> {
                Guardian g = new Guardian(DocumentType.CC, "99910000001", "Luis", "Pérez", "padre", "3001234567");
                return "Padre".equals(g.getRelationship())
                        && throwsValidation(() -> g.setRelationship("Vecino"))
                        && throwsValidation(() -> g.setRelationship("Tutor"));
            });
            check("Allergies and medical conditions must be letters", () ->
                    throwsValidation(() -> baseBuilder("99900000001", "Ana").allergies("3254325435").build())
                    && throwsValidation(() -> baseBuilder("99900000001", "Ana").medicalConditions("3432432432").build())
                    && "Polen".equals(baseBuilder("99900000001", "Ana").allergies("Polen").build().getAllergies()));
            check("Guardian occupation accepts words with accents", () -> {
                Guardian g = new Guardian(DocumentType.CC, "99910000001", "Luis", "Pérez", "Tío", "3001234567");
                g.setOccupation("Ingeniero/a de sistemas");
                return "Ingeniero/a de sistemas".equals(g.getOccupation());
            });
            check("Guardian without a valid phone is rejected", () -> throwsValidation(() ->
                    new Guardian(DocumentType.CC, "99910000001", "Luis", "Pérez", "Padre", "abc")));

            // ---- register ----
            Student ana = student("99900000001", "Ana");
            Guardian mother = guardian("99910000001", "Marta", "Madre");
            Result<Student> registered = students.register(ana, new ArrayList<>(List.of(new StudentGuardian(mother, true, true))));
            check("Register a student with one guardian", () -> registered.isSuccess() && ana.getId() != null && mother.getId() != null);
            check("Register without guardians fails", () -> !students.register(student("99900000002", "Sin"), new ArrayList<>()).isSuccess());
            check("Duplicate document fails with a message", () ->
                    !students.register(student("99900000001", "Otra"), new ArrayList<>(List.of(new StudentGuardian(guardian("99910000009", "X", "Abuelo"), true, true)))).isSuccess());
            check("The failed registration saved nothing (rollback)", () ->
                    !students.findGuardian("99910000009").getData().isPresent());

            // ---- siblings share a guardian ----
            Student bob = student("99900000003", "Bob");
            Guardian sameMother = students.findGuardian("99910000001").getData().orElseThrow();
            check("A sibling reuses the existing guardian", () ->
                    students.register(bob, new ArrayList<>(List.of(new StudentGuardian(sameMother, true, true)))).isSuccess()
                            && students.studentsOf(sameMother).getData().size() == 2);

            // ---- guardians rules ----
            Guardian father = guardian("99910000002", "Pedro", "Padre");
            check("Add a second guardian", () -> students.addGuardian(ana, new StudentGuardian(father, false, true)).isSuccess()
                    && students.guardiansOf(ana).getData().size() == 2);
            check("Adding the same guardian twice fails", () -> !students.addGuardian(ana, new StudentGuardian(father, false, true)).isSuccess());
            StudentGuardian principal = students.guardiansOf(ana).getData().stream().filter(StudentGuardian::isPrincipal).findFirst().orElseThrow();
            check("The principal guardian cannot be removed", () -> !students.removeGuardian(ana, principal).isSuccess());
            check("Change the principal guardian", () -> {
                StudentGuardian other = students.guardiansOf(ana).getData().stream().filter(l -> !l.isPrincipal()).findFirst().orElseThrow();
                return students.changePrincipalGuardian(ana, other).isSuccess()
                        && students.guardiansOf(ana).getData().stream().filter(StudentGuardian::isPrincipal).count() == 1;
            });
            check("A non-principal guardian can be removed", () -> {
                StudentGuardian nonPrincipal = students.guardiansOf(ana).getData().stream().filter(l -> !l.isPrincipal()).findFirst().orElseThrow();
                return students.removeGuardian(ana, nonPrincipal).isSuccess() && students.guardiansOf(ana).getData().size() == 1;
            });
            check("The DAO refuses to remove the last guardian", () -> {
                try {
                    daos.studentDao().removeGuardian(ana.getId(), mother.getId());
                    return false;
                } catch (com.littlehorizons.erp.exception.DaoException e) {
                    return e.getMessage().contains("al menos un acudiente");
                }
            });

            // ---- read / update ----
            check("Search finds the student by name and by document", () ->
                    students.search("ana").getData().stream().anyMatch(s -> s.getDocumentNumber().equals("99900000001"))
                            && students.search("9990000000").getData().size() >= 2);
            check("Search with % does not match everything", () -> students.search("%").getData().isEmpty());
            check("Update a student's phone", () -> {
                ana.setPhone("3109998877");
                Student reloaded = null;
                if (students.update(ana).isSuccess()) {
                    reloaded = students.findByDocument("99900000001").getData();
                }
                return reloaded != null && "3109998877".equals(reloaded.getPhone());
            });
            check("Accents survive the round trip to the database", () -> {
                Student accent = baseBuilder("99900000004", "José").lastName("Muñoz").build();
                return students.register(accent, new ArrayList<>(List.of(new StudentGuardian(guardian("99910000004", "Ángela", "Madre"), true, true)))).isSuccess()
                        && students.findByDocument("99900000004").getData().getLastName().equals("Muñoz");
            });

            // ---- enrollment ----
            List<GradeLevel> grades = enrollments.grades().getData();
            GradeLevel fifth = grades.stream().filter(g -> g.getName().equals("Quinto")).findFirst().orElseThrow();
            GradeLevel sixth = grades.stream().filter(g -> g.getName().equals("Sexto")).findFirst().orElseThrow();
            Result<Enrollment> enrolled = enrollments.enroll(ana, fifth, year);
            check("Enroll a student in a grade and year", enrolled::isSuccess);
            check("A second enrollment in the same year fails", () -> !enrollments.enroll(ana, sixth, year).isSuccess());
            check("The grade roster contains the student", () ->
                    enrollments.byGradeAndYear(fifth, year).getData().stream().anyMatch(e -> e.getStudent().getDocumentNumber().equals("99900000001")));

            // ---- subject registration ----
            List<Subject> available = registrations.availableSubjects(ana, year).getData();
            check("Available subjects belong to the student's grade only", () ->
                    !available.isEmpty() && available.stream().allMatch(s -> s.getGradeLevel().getId() == fifth.getId()));
            Subject math = available.get(0);
            check("Register in one subject for a period", () -> registrations.register(ana, math, year, AcademicPeriod.FIRST, "A").isSuccess());
            check("Registering twice in the same period fails", () -> !registrations.register(ana, math, year, AcademicPeriod.FIRST, null).isSuccess());
            Subject otherGradeSubject = daos.subjectDao().findActiveByGrade(sixth.getId()).get(0);
            check("A subject of another grade is refused", () -> {
                Result<SubjectRegistration> r = registrations.register(ana, otherGradeSubject, year, AcademicPeriod.FIRST, null);
                return !r.isSuccess() && r.getMessage().contains("pertenece");
            });
            check("Register in all subjects of the grade", () -> {
                Result<Integer> all = registrations.registerAll(ana, year, AcademicPeriod.SECOND);
                return all.isSuccess() && all.getData() == available.size();
            });
            check("Student without enrollment cannot register", () -> !registrations.register(bob, math, year, AcademicPeriod.FIRST, null).isSuccess());
            check("Registrations are listed by period", () -> registrations.byStudent(ana, year).getData().size() == available.size() + 1);

            // ---- retire ----
            check("Retire closes enrollments and registrations", () -> {
                Result<Void> retired = students.retire(ana);
                return retired.isSuccess()
                        && enrollments.byStudent(ana).getData().get(0).getStatus() == EnrollmentStatus.WITHDRAWN
                        && registrations.byStudent(ana, year).getData().stream().noneMatch(r -> r.getStatus().getDbLabel().equals("Activa"));
            });
            check("A retired student cannot be edited or enrolled", () ->
                    !students.update(ana).isSuccess() && !enrollments.enroll(ana, fifth, year - 1).isSuccess());
            check("Retiring twice fails", () -> !students.retire(ana).isSuccess());
        } finally {
            cleanUp();
        }
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Student.Builder baseBuilder(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2015, 5, 20)).admissionDate(LocalDate.now());
    }

    private static Student student(String document, String firstName) {
        return baseBuilder(document, firstName).build();
    }

    private static Guardian guardian(String document, String firstName, String relationship) {
        return new Guardian(DocumentType.CC, document, firstName, "Prueba", relationship, "3001234567");
    }

    private static boolean throwsValidation(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ValidationException e) {
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

    private static void check(String name, java.util.function.BooleanSupplier test) {
        check(name, (Callable<Boolean>) test::getAsBoolean);
    }

    /** Deletes everything created by this check. Cascades remove links, enrollments and registrations. */
    private static void cleanUp() throws SQLException, com.littlehorizons.erp.exception.DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            execute(connection, "DELETE FROM estudiantes WHERE numero_documento LIKE '" + TEST_PREFIX + "%'");
            execute(connection, "DELETE FROM acudientes WHERE numero_documento LIKE '9991%'");
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
