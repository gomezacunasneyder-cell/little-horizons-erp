package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.ClubMembershipController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.club.ClubAnalyzer;
import com.littlehorizons.erp.controller.club.ClubCatalog;
import com.littlehorizons.erp.controller.club.ClubOccupancy;
import com.littlehorizons.erp.controller.club.ClubSummary;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.ClubMembership;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.MembershipStatus;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.model.enums.Weekday;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Manual check of module 2.1 (plain Java). It creates its own teachers (documents starting with
 * 9950), students (9951) and guardians (9952) and clubs named «Club Zzprueba ...», and deletes
 * everything at the end. The last checks read the sample data of docs/database.sql. Run main()
 * from IntelliJ with the database loaded and config.txt in place.
 */
public final class ClubModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        ClubController clubs = new ClubController(daos.clubDao(), logger);
        ClubMembershipController memberships = new ClubMembershipController(daos.clubMembershipDao(), daos.clubDao(), logger);
        LocalDate today = LocalDate.now();

        try {
            cleanUp();

            // ---- enum, entities and the pure calculations ----
            check("The membership state mirrors the database labels", () ->
                    MembershipStatus.fromDbLabel("Activo") == MembershipStatus.ACTIVE
                            && MembershipStatus.fromDbLabel("Retirado") == MembershipStatus.WITHDRAWN
                            && MembershipStatus.values().length == 2);
            Teacher probeTeacher = teacher("99500000009", "Probe", "probe.club@test.edu");
            check("A club needs a letters-only name, a description, a capacity of 1 to 200 and a schedule", () ->
                    throwsValidation(() -> new Club("Club 2026", "Algo", probeTeacher, 10, "Martes 15:00-17:00"))
                            && throwsValidation(() -> new Club("   ", "Algo", probeTeacher, 10, "Martes 15:00-17:00"))
                            && throwsValidation(() -> new Club("Club Zzprueba", "  ", probeTeacher, 10, "Martes 15:00-17:00"))
                            && throwsValidation(() -> new Club("Club Zzprueba", "Algo", probeTeacher, 0, "Martes 15:00-17:00"))
                            && throwsValidation(() -> new Club("Club Zzprueba", "Algo", probeTeacher, 201, "Martes 15:00-17:00"))
                            && throwsValidation(() -> new Club("Club Zzprueba", "Algo", probeTeacher, 10, " "))
                            && throwsValidation(() -> new Club("Club Zzprueba", "x".repeat(501), probeTeacher, 10, "Martes 15:00-17:00"))
                            && new Club("Club Zzprueba", "Algo", probeTeacher, 200, "Martes 15:00-17:00").getMaxCapacity() == 200);
            check("The schedule is built from day and hours, and the end must be after the start", () ->
                    Club.formatSchedule(Weekday.TUESDAY, LocalTime.of(15, 0), LocalTime.of(17, 0)).equals("Martes 15:00-17:00")
                            && throwsValidation(() -> Club.formatSchedule(Weekday.TUESDAY, LocalTime.of(17, 0), LocalTime.of(15, 0)))
                            && throwsValidation(() -> Club.formatSchedule(Weekday.TUESDAY, LocalTime.of(15, 0), LocalTime.of(15, 0))));
            Student probeStudent = student("99510000009", "Probe");
            Club probeClub = new Club("Club Zzprueba", "Algo", probeTeacher, 5, "Martes 15:00-17:00");
            check("The joining date cannot be in the future", () ->
                    throwsValidation(() -> new ClubMembership(probeStudent, probeClub, today.plusDays(1))));
            check("A withdrawal needs a date between the joining date and today, and happens only once", () -> {
                ClubMembership m = new ClubMembership(probeStudent, probeClub, today.minusDays(10));
                boolean rules = throwsValidation(() -> m.withdraw(today.minusDays(11), null))
                        && throwsValidation(() -> m.withdraw(today.plusDays(1), null))
                        && throwsValidation(() -> m.withdraw(today, "x".repeat(301)))
                        && m.isActive();
                m.withdraw(today.minusDays(3), "  Cambio de horario  ");
                return rules && !m.isActive() && m.getStatus() == MembershipStatus.WITHDRAWN
                        && "Cambio de horario".equals(m.getWithdrawalReason())
                        && throwsValidation(() -> m.withdraw(today, null));
            });
            check("A blank reason is stored as none", () -> {
                ClubMembership m = new ClubMembership(probeStudent, probeClub, today.minusDays(2));
                m.withdraw(today, "   ");
                return m.getWithdrawalReason() == null;
            });
            check("The occupancy is sorted (open first, Spanish alphabetical) and totalled with streams", () -> {
                Club a = clubNamed(probeTeacher, "Club Zeta", 4, true, 1);
                Club b = clubNamed(probeTeacher, "Club Álamo", 3, true, 2);
                Club c = clubNamed(probeTeacher, "Club Cerrado", 10, false, 3);
                Club d = clubNamed(probeTeacher, "Club Beta", 2, true, 4);
                ClubCatalog catalog = ClubAnalyzer.catalog(List.of(a, b, c, d), Map.of(1, 1, 2, 3, 4, 2, 3, 0));
                List<String> names = catalog.clubs().stream().map(o -> o.club().getName()).toList();
                ClubSummary s = catalog.summary();
                ClubOccupancy zeta = catalog.clubs().stream().filter(o -> o.club() == a).findFirst().orElseThrow();
                return names.equals(List.of("Club Álamo", "Club Beta", "Club Zeta", "Club Cerrado"))
                        && s.totalClubs() == 4 && s.activeClubs() == 3 && s.fullClubs() == 2
                        && s.activeMemberships() == 6 && s.availableSpots() == 3
                        && zeta.availableSpots() == 3 && !zeta.isFull();
            });

            // ---- setup ----
            Teacher ana = teacher("99500000001", "Ana", "ana.club@test.edu");
            Teacher luis = teacher("99500000002", "Luis", "luis.club@test.edu");
            check("Register two teachers; one goes on leave", () -> teachers.register(ana).isSuccess()
                    && teachers.register(luis).isSuccess() && teachers.changeStatus(luis, TeacherStatus.ON_LEAVE).isSuccess());
            List<Student> pupils = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Student s = student("9951000000" + (i % 10) + (i / 10), "Alumno" + letter(i));
                pupils.add(s);
                String suffix = String.format("%02d", i);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "99520000" + suffix, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
            }
            check("Register ten students", () -> pupils.stream().allMatch(s -> s.getId() != null));

            // ---- catalog ----
            Club science = new Club("Club Zzprueba Ciencias", "Experimentos", ana, 3, "Martes 15:00-17:00");
            check("Create a club", () -> clubs.create(science).isSuccess() && science.getId() != null);
            check("A club name cannot be repeated (even in other case)", () -> {
                Result<Club> r = clubs.create(new Club("club zzprueba ciencias", "Otro", ana, 5, "Lunes 14:00-15:00"));
                return !r.isSuccess() && r.getMessage().contains("Ya existe");
            });
            check("The name check tells whether a name is taken (and ignores the club being edited)", () ->
                    clubs.nameTaken("Club Zzprueba Ciencias", null).getData()
                            && !clubs.nameTaken("Club Zzprueba Ciencias", science.getId()).getData()
                            && !clubs.nameTaken("Club Zzprueba Libre", null).getData());
            check("The teacher in charge must be active", () -> {
                Result<Club> r = clubs.create(new Club("Club Zzprueba Licencia", "Algo", luis, 5, "Lunes 14:00-15:00"));
                return !r.isSuccess() && r.getMessage().contains("activo");
            });
            check("Search finds a club by name or by teacher, and by active state", () ->
                    clubs.search("zzprueba cien").getData().size() == 1
                            && clubs.search("Ana").getData().stream().anyMatch(c -> c.getId().equals(science.getId()))
                            && clubs.searchActive("zzprueba cien").getData().size() == 1
                            && !clubs.search("  ").isSuccess());

            // ---- enrolling ----
            Student s1 = pupils.get(0);
            Student s2 = pupils.get(1);
            Student s3 = pupils.get(2);
            Student s4 = pupils.get(3);
            Result<ClubMembership> first = memberships.enroll(s1, science, today.minusDays(5));
            check("Enroll a student", () -> first.isSuccess() && first.getData().getId() != null
                    && first.getMessage().contains("1 de 3")
                    && daos.clubMembershipDao().existsActive(s1.getId(), science.getId()));
            check("The joining date read back is the one of the club, not the admission date of the student", () -> {
                ClubMembership saved = memberships.byStudent(s1).getData().get(0);
                return saved.getJoinDate().equals(today.minusDays(5)) && !saved.getJoinDate().equals(s1.getAdmissionDate());
            });
            check("The same student cannot be active twice in the same club (controller and database)", () -> {
                Result<ClubMembership> again = memberships.enroll(s1, science, today);
                boolean duplicateByDb = false;
                try {
                    execute("INSERT INTO membresias_club (id_estudiante, id_club, fecha_ingreso) VALUES ("
                            + s1.getId() + ", " + science.getId() + ", CURRENT_DATE)");
                } catch (SQLException e) {
                    duplicateByDb = true;
                }
                return !again.isSuccess() && again.getMessage().contains("ya es miembro") && duplicateByDb;
            });
            check("A future joining date is rejected with a message", () -> !memberships.enroll(s2, science, today.plusDays(2)).isSuccess());
            check("A student can be in several clubs", () -> {
                Club arts = new Club("Club Zzprueba Artes", "Pintura", ana, 5, "Jueves 14:00-16:00");
                return clubs.create(arts).isSuccess() && memberships.enroll(s1, arts, today).isSuccess()
                        && memberships.activeByStudent(s1).getData().size() == 2;
            });
            check("Only active students can enroll", () -> {
                Student inactive = pupils.get(9);
                inactive.setStatus(StudentStatus.WITHDRAWN);
                Result<ClubMembership> r = memberships.enroll(inactive, science, today);
                inactive.setStatus(StudentStatus.ACTIVE);
                return !r.isSuccess() && r.getMessage().contains("activos");
            });
            check("Fill the club to its capacity, then the next student is refused", () -> {
                boolean second = memberships.enroll(s2, science, today).isSuccess();
                boolean third = memberships.enroll(s3, science, today).isSuccess();
                Result<ClubMembership> fourth = memberships.enroll(s4, science, today);
                return second && third && !fourth.isSuccess() && fourth.getMessage().contains("lleno")
                        && daos.clubDao().countActiveMembers(science.getId()) == 3;
            });
            check("The catalog shows the club as full with no spots left", () -> {
                ClubOccupancy o = occupancyOf(clubs, science);
                return o.activeMembers() == 3 && o.availableSpots() == 0 && o.isFull();
            });
            check("The capacity cannot be reduced below the active members", () -> {
                Club smaller = copy(science, 2);
                Result<Club> r = clubs.update(smaller);
                return !r.isSuccess() && r.getMessage().contains("3 miembros");
            });
            check("The database also refuses a capacity below the active members (inside a locked transaction)", () -> {
                try {
                    daos.clubDao().update(copy(science, 2));
                    return false;
                } catch (DaoException e) {
                    return e.getMessage().contains("miembros activos");
                }
            });
            check("A club cannot be closed while it has active members", () -> {
                Result<Club> r = clubs.setActive(science, false);
                return !r.isSuccess() && r.getMessage().contains("activo") && science.isActive();
            });

            // ---- withdrawing, history and coming back ----
            ClubMembership s2Membership = memberships.activeByStudent(s2).getData().get(0);
            check("A withdrawal before the joining date is rejected and leaves the membership active", () ->
                    !memberships.withdraw(s2Membership, s2Membership.getJoinDate().minusDays(1), null).isSuccess()
                            && daos.clubMembershipDao().existsActive(s2.getId(), science.getId()));
            check("Withdraw a student: the state, the date and the reason are saved", () -> {
                Result<ClubMembership> r = memberships.withdraw(s2Membership, today, "Cambio de horario");
                ClubMembership saved = memberships.byStudent(s2).getData().get(0);
                return r.isSuccess() && saved.getStatus() == MembershipStatus.WITHDRAWN
                        && saved.getWithdrawalDate().equals(today) && "Cambio de horario".equals(saved.getWithdrawalReason());
            });
            check("A withdrawal frees a spot", () -> occupancyOf(clubs, science).availableSpots() == 1);
            check("A withdrawn student can come back and the history keeps both stays", () -> {
                boolean back = memberships.enroll(s2, science, today).isSuccess();
                List<ClubMembership> history = memberships.byStudent(s2).getData();
                return back && history.size() == 2 && history.stream().filter(ClubMembership::isActive).count() == 1;
            });
            check("The members of a club list the active ones first", () -> {
                List<ClubMembership> members = memberships.byClub(science).getData();
                return members.size() == 4 && members.subList(0, 3).stream().allMatch(ClubMembership::isActive)
                        && !members.get(3).isActive();
            });
            ClubMembership s3Membership = memberships.activeByStudent(s3).getData().stream()
                    .filter(m -> m.getClub().getId().equals(science.getId())).findFirst().orElseThrow();
            check("Withdrawing an already withdrawn membership is refused", () -> {
                ClubMembership stale = memberships.byStudent(s3).getData().get(0);
                memberships.withdraw(s3Membership, today, null);
                Result<ClubMembership> r = memberships.withdraw(stale, today, null);
                return !r.isSuccess() && r.getMessage().contains("retirad");
            });

            // ---- closing and reopening ----
            Club chess = new Club("Club Zzprueba Ajedrez", "Partidas", ana, 4, "Lunes 14:00-15:00");
            check("An empty club can be closed, nobody can enroll in it, and it can be reopened", () -> {
                clubs.create(chess);
                boolean closed = clubs.setActive(chess, false).isSuccess() && !chess.isActive();
                Result<ClubMembership> blocked = memberships.enroll(s4, chess, today);
                boolean twice = !clubs.setActive(chess, false).isSuccess();
                boolean notFound = clubs.searchActive("zzprueba ajedrez").getData().isEmpty()
                        && clubs.search("zzprueba ajedrez").getData().size() == 1;
                boolean reopened = clubs.setActive(chess, true).isSuccess() && chess.isActive();
                return closed && !blocked.isSuccess() && blocked.getMessage().contains("cerrado") && twice && notFound && reopened;
            });
            check("Editing a club changes its data and keeps its state", () -> {
                Club edited = copy(chess, 6);
                edited.setDescription("Partidas y torneos");
                Result<Club> r = clubs.update(edited);
                Club reloaded = daos.clubDao().findById(chess.getId()).orElseThrow();
                return r.isSuccess() && reloaded.getMaxCapacity() == 6 && reloaded.getDescription().equals("Partidas y torneos")
                        && reloaded.isActive() && reloaded.getSupervisor().getId().equals(ana.getId());
            });
            check("A club cannot be renamed with a name that another club uses", () -> {
                Club clash = copy(chess, 6);
                clash.setName("Club Zzprueba Ciencias");
                return !clubs.update(clash).isSuccess();
            });
            check("A club cannot be edited to a teacher who is not active", () -> {
                Club toLeave = copy(chess, 6);
                toLeave.setSupervisor(luis);
                return !clubs.update(toLeave).isSuccess();
            });

            // ---- concurrency: the capacity holds when many enroll at the same moment ----
            Club race = new Club("Club Zzprueba Carrera", "Prueba de cupo", ana, 3, "Viernes 14:00-15:00");
            clubs.create(race);
            List<Student> runners = pupils.subList(0, 8);
            check("Eight students enroll at the same time in a club of 3: exactly 3 get in", () -> {
                ExecutorService pool = Executors.newFixedThreadPool(runners.size());
                CyclicBarrier startTogether = new CyclicBarrier(runners.size());
                List<Future<Boolean>> results = new ArrayList<>();
                for (Student runner : runners) {
                    results.add(pool.submit(() -> {
                        startTogether.await();
                        return memberships.enroll(runner, race, today).isSuccess();
                    }));
                }
                long accepted = 0;
                for (Future<Boolean> f : results) {
                    if (f.get()) {
                        accepted++;
                    }
                }
                pool.shutdown();
                return accepted == 3 && daos.clubDao().countActiveMembers(race.getId()) == 3
                        && scalar("SELECT COUNT(*) FROM membresias_club WHERE id_club = " + race.getId()) == 3;
            });

            // ---- the sample data of docs/database.sql ----
            check("The sample data has at least 30 clubs and 30 memberships", () ->
                    scalar("SELECT COUNT(*) FROM clubes WHERE nombre_club NOT LIKE 'Club Zzprueba%'") >= 30
                            && scalar("SELECT COUNT(*) FROM membresias_club WHERE id_estudiante NOT IN (SELECT id_estudiante FROM estudiantes WHERE numero_documento LIKE '9951%')") >= 30);
            check("Sample: «Club de Ajedrez» and «Club de Teatro» are full, «Club de Robótica» has one spot", () -> {
                ClubCatalog catalog = clubs.catalog().getData();
                return byName(catalog, "Club de Ajedrez").isFull() && byName(catalog, "Club de Teatro").isFull()
                        && byName(catalog, "Club de Robótica").availableSpots() == 1;
            });
            check("Sample: enrolling in a full club of the sample data is refused", () -> {
                Club chessSample = clubs.search("Club de Ajedrez").getData().get(0);
                Result<ClubMembership> r = memberships.enroll(pupils.get(4), chessSample, today);
                return !r.isSuccess() && r.getMessage().contains("lleno");
            });
            check("Sample: closed clubs have no active members and every club has an active teacher", () ->
                    scalar("SELECT COUNT(*) FROM membresias_club m JOIN clubes c ON c.id_club = m.id_club "
                            + "WHERE c.esta_activo = 0 AND m.estado_membresia = 'Activo'") == 0
                            && scalar("SELECT COUNT(*) FROM clubes c JOIN profesores p ON p.id_profesor = c.id_profesor_supervisor "
                            + "WHERE c.esta_activo = 1 AND p.estado <> 'Activo' AND c.nombre_club NOT LIKE 'Club Zzprueba%'") == 0);
            check("Sample: no club is over its capacity", () ->
                    scalar("SELECT COUNT(*) FROM (SELECT c.id_club FROM clubes c JOIN membresias_club m ON m.id_club = c.id_club "
                            + "AND m.estado_membresia = 'Activo' GROUP BY c.id_club, c.capacidad_maxima "
                            + "HAVING COUNT(*) > c.capacidad_maxima) x") == 0);
            check("Sample: a student in several clubs and a student who left and came back are listed", () ->
                    memberships.byStudent(daos.studentDao().findByDocumentNumber("1097258444").orElseThrow()).getData().stream()
                            .filter(ClubMembership::isActive).count() >= 3
                            && memberships.byStudent(daos.studentDao().findByDocumentNumber("1116886561").orElseThrow()).getData().stream()
                            .anyMatch(m -> !m.isActive() && m.getClub().getName().equals("Club de Ciencias")));
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static String letter(int i) {
        return String.valueOf((char) ('A' + i));
    }

    private static Student student(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2012, 5, 20)).admissionDate(LocalDate.now()).build();
    }

    private static Teacher teacher(String document, String firstName, String email) {
        return new Teacher(DocumentType.CC, document, firstName, "Prueba", email, "Matemáticas", LocalDate.of(2020, 1, 1));
    }

    private static Club clubNamed(Teacher teacher, String name, int capacity, boolean active, int id) {
        Club club = new Club(name, "Algo", teacher, capacity, "Martes 15:00-17:00");
        club.setId(id);
        club.setActive(active);
        return club;
    }

    /** A copy of the club with another capacity (same id, teacher and state), as the edit form builds it. */
    private static Club copy(Club club, int capacity) {
        Club copy = new Club(club.getName(), club.getDescription(), club.getSupervisor(), capacity, club.getMeetingSchedule());
        copy.setId(club.getId());
        copy.setActive(club.isActive());
        return copy;
    }

    private static ClubOccupancy occupancyOf(ClubController clubs, Club club) {
        return clubs.catalog().getData().clubs().stream().filter(o -> o.club().getId().equals(club.getId()))
                .findFirst().orElseThrow();
    }

    private static ClubOccupancy byName(ClubCatalog catalog, String name) {
        return catalog.clubs().stream().filter(o -> o.club().getName().equals(name)).findFirst().orElseThrow();
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

    /** Deletes what this check created. The clubs first: they refer to the teachers (memberships go with them). */
    private static void cleanUp() throws SQLException, DaoException {
        execute("DELETE FROM clubes WHERE nombre_club LIKE 'Club Zzprueba%'");
        execute("DELETE FROM estudiantes WHERE numero_documento LIKE '9951%'");
        execute("DELETE FROM acudientes WHERE numero_documento LIKE '9952%'");
        execute("DELETE FROM profesores WHERE numero_documento LIKE '9950%'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
