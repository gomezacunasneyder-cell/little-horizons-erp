package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.ScheduleController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.TeacherWorkload;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.ScheduleSlot;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.model.enums.Weekday;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Manual check of module 1.2 (plain Java). It creates its own teachers (documents starting with
 * 9980), uses the year after the current one so the sample data is not touched, and deletes
 * everything at the end. Run main() from IntelliJ with the database loaded and config.txt in place.
 */
public final class TeacherModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        AssignmentController assignments = new AssignmentController(daos.teachingAssignmentDao(),
                daos.scheduleDao(), daos.subjectDao(), daos.gradeLevelDao(), logger);
        ScheduleController schedules = new ScheduleController(daos.scheduleDao(), logger);
        int year = LocalDate.now().getYear() + 1;

        try {
            cleanUp();

            // ---- entity validation ----
            check("Teacher with TI document is rejected", () -> throwsValidation(() ->
                    new Teacher(DocumentType.TI, "99800000001", "Ana", "Prueba", "a@test.edu", "Matemáticas", LocalDate.of(2020, 1, 1))));
            check("Teacher without e-mail is rejected", () -> throwsValidation(() ->
                    new Teacher(DocumentType.CC, "99800000001", "Ana", "Prueba", " ", "Matemáticas", LocalDate.of(2020, 1, 1))));
            check("Teacher hired in the future is rejected", () -> throwsValidation(() ->
                    new Teacher(DocumentType.CC, "99800000001", "Ana", "Prueba", "a@test.edu", "Matemáticas", LocalDate.now().plusDays(1))));
            check("Teacher without specialization is rejected", () -> throwsValidation(() ->
                    new Teacher(DocumentType.CC, "99800000001", "Ana", "Prueba", "a@test.edu", "", LocalDate.of(2020, 1, 1))));
            check("Specialization with digits is rejected", () -> throwsValidation(() ->
                    new Teacher(DocumentType.CC, "99800000001", "Ana", "Prueba", "a@test.edu", "3423423", LocalDate.of(2020, 1, 1))));
            check("Invalid section is rejected", () -> throwsValidation(() ->
                    new TeachingAssignment(teacher("99800000001", "Ana", "a@test.edu"), fakeSubject(), year, "A B")));

            // ---- directory ----
            Teacher ana = teacher("99800000001", "Ana", "ana.check@test.edu");
            Teacher luis = teacher("99800000002", "Luis", "luis.check@test.edu");
            Teacher eva = teacher("99800000003", "Eva", "eva.check@test.edu");
            check("Register a teacher", () -> teachers.register(ana).isSuccess() && ana.getId() != null);
            check("Register two more teachers", () -> teachers.register(luis).isSuccess() && teachers.register(eva).isSuccess());
            check("Duplicate document is refused", () -> !teachers.register(teacher("99800000001", "Otra", "otra@test.edu")).isSuccess());
            check("Duplicate e-mail is refused", () -> !teachers.register(teacher("99800000009", "Otra", "ana.check@test.edu")).isSuccess());
            check("Search finds by name, document and specialization", () ->
                    teachers.search("ana").getData().stream().anyMatch(t -> t.getDocumentNumber().equals("99800000001"))
                            && teachers.search("99800000002").getData().size() == 1
                            && !teachers.search("Matemáticas").getData().isEmpty());
            check("Update the phone and persist it", () -> {
                ana.setPhone("3105550000");
                return teachers.update(ana).isSuccess()
                        && "3105550000".equals(teachers.findByDocument("99800000001").getData().getPhone());
            });
            check("Accents survive the round trip", () -> {
                Teacher t = new Teacher(DocumentType.CC, "99800000004", "José", "Muñoz", "jose.check@test.edu", "Educación Física", LocalDate.of(2019, 2, 3));
                return teachers.register(t).isSuccess() && teachers.findByDocument("99800000004").getData().getLastName().equals("Muñoz");
            });
            check("Change the status and refuse the same one", () ->
                    teachers.changeStatus(eva, TeacherStatus.ON_LEAVE).isSuccess()
                            && teachers.findByDocument("99800000003").getData().getStatus() == TeacherStatus.ON_LEAVE
                            && !teachers.changeStatus(eva, TeacherStatus.ON_LEAVE).isSuccess());

            // ---- assignments ----
            List<GradeLevel> grades = assignments.grades().getData();
            GradeLevel fifth = grades.stream().filter(g -> g.getName().equals("Quinto")).findFirst().orElseThrow();
            GradeLevel sixth = grades.stream().filter(g -> g.getName().equals("Sexto")).findFirst().orElseThrow();
            List<Subject> fifthSubjects = assignments.subjectsOf(fifth).getData();
            List<Subject> sixthSubjects = assignments.subjectsOf(sixth).getData();
            Subject fifthA = fifthSubjects.get(0);
            Subject fifthB = fifthSubjects.get(1);
            Subject sixthA = sixthSubjects.get(0);

            Result<TeachingAssignment> first = assignments.assign(ana, fifthA, year, "a");
            check("Assign a subject to an active teacher (section is stored in upper case)", () ->
                    first.isSuccess() && first.getData().getSection().equals("A") && first.getData().getId() != null);
            check("The same subject, year and section cannot have two teachers", () -> !assignments.assign(luis, fifthA, year, "A").isSuccess());
            check("The same subject in another section can have another teacher", () -> assignments.assign(luis, fifthA, year, "B").isSuccess());
            check("A teacher on leave cannot receive assignments", () -> !assignments.assign(eva, fifthB, year, "A").isSuccess());
            Result<TeachingAssignment> second = assignments.assign(luis, fifthB, year, "A");
            Result<TeachingAssignment> third = assignments.assign(ana, sixthA, year, "A");
            check("A teacher can have several subjects", () -> second.isSuccess() && third.isSuccess()
                    && assignments.byTeacher(ana, year).getData().size() == 2);
            check("A subject lists its teachers by section", () -> assignments.bySubject(fifthA, year).getData().size() == 2);

            // ---- schedules ----
            TeachingAssignment anaFifthA = first.getData();
            TeachingAssignment luisFifthA = second.getData();
            TeachingAssignment anaSixthA = third.getData();
            LocalTime seven = LocalTime.of(7, 0);
            LocalTime eight = LocalTime.of(8, 0);
            check("Add a class block", () -> schedules.addSlot(anaFifthA, Weekday.MONDAY, seven, eight).isSuccess());
            check("A block that touches the previous one is allowed", () ->
                    schedules.addSlot(anaFifthA, Weekday.MONDAY, eight, LocalTime.of(9, 0)).isSuccess());
            check("Teacher conflict is detected (other group, same time)", () -> {
                Result<ScheduleSlot> r = schedules.addSlot(anaSixthA, Weekday.MONDAY, LocalTime.of(7, 30), LocalTime.of(8, 30));
                return !r.isSuccess() && r.getMessage().contains("el profesor ya dicta");
            });
            check("Group conflict is detected (other teacher, same group and time)", () -> {
                Result<ScheduleSlot> r = schedules.addSlot(luisFifthA, Weekday.MONDAY, LocalTime.of(7, 30), LocalTime.of(8, 0));
                return !r.isSuccess() && r.getMessage().contains("ya tiene");
            });
            check("The same time on another day is allowed", () -> schedules.addSlot(anaSixthA, Weekday.TUESDAY, seven, eight).isSuccess());
            check("The same time for a different section is allowed", () -> {
                TeachingAssignment luisFifthB = assignments.bySubject(fifthA, year).getData().stream()
                        .filter(a -> a.getSection().equals("B")).findFirst().orElseThrow();
                return schedules.addSlot(luisFifthB, Weekday.MONDAY, seven, eight).isSuccess();
            });
            check("End before start is rejected", () -> !schedules.addSlot(luisFifthA, Weekday.FRIDAY, eight, seven).isSuccess());
            check("A block outside the school day is rejected", () -> !schedules.addSlot(luisFifthA, Weekday.FRIDAY, LocalTime.of(4, 0), LocalTime.of(5, 0)).isSuccess());
            check("A block shorter than 30 minutes is rejected", () -> !schedules.addSlot(luisFifthA, Weekday.FRIDAY, seven, LocalTime.of(7, 10)).isSuccess());
            check("The weekly schedule is grouped by day and sorted by time", () -> {
                Map<Weekday, List<ScheduleSlot>> week = schedules.weeklyScheduleOf(ana, year).getData();
                List<ScheduleSlot> monday = week.get(Weekday.MONDAY);
                return week.keySet().stream().toList().equals(List.of(Weekday.MONDAY, Weekday.TUESDAY))
                        && monday.size() == 2 && monday.get(0).getStart().equals(seven);
            });
            check("The group schedule shows the classes of the group", () ->
                    schedules.weeklyScheduleOfGroup(fifth, "a", year).getData().get(Weekday.MONDAY).size() == 2);
            check("Workload adds the scheduled hours per assignment", () -> {
                TeacherWorkload workload = assignments.workload(ana, year).getData();
                return workload.lines().size() == 2 && Math.abs(workload.totalWeeklyHours() - 3.0) < 0.001;
            });
            check("Removing a block frees the time", () -> {
                ScheduleSlot slot = schedules.slotsOf(anaFifthA).getData().get(0);
                return schedules.removeSlot(slot).isSuccess()
                        && schedules.addSlot(anaSixthA, Weekday.MONDAY, LocalTime.of(7, 30), LocalTime.of(8, 0)).isSuccess();
            });
            check("Removing an assignment deletes its schedule", () ->
                    assignments.remove(anaSixthA).isSuccess()
                            && schedules.slotsOf(anaSixthA).getData().isEmpty()
                            && assignments.byTeacher(ana, year).getData().size() == 1);
        } finally {
            cleanUp();
        }
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Teacher teacher(String document, String firstName, String email) {
        return new Teacher(DocumentType.CC, document, firstName, "Prueba", email, "Matemáticas", LocalDate.of(2020, 1, 15));
    }

    private static Subject fakeSubject() {
        return new Subject(1, "X-1", "X", new GradeLevel(1, "Preescolar", 0), 2, true);
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

    /** Deletes the test teachers. Their assignments and schedule blocks disappear by cascade. */
    private static void cleanUp() throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM profesores WHERE numero_documento LIKE '9980%'")) {
            statement.executeUpdate();
        }
    }
}
