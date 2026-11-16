package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.EventParticipationController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.event.EventAnalyzer;
import com.littlehorizons.erp.controller.event.EventCalendar;
import com.littlehorizons.erp.controller.event.EventCalendarEntry;
import com.littlehorizons.erp.controller.event.EventResults;
import com.littlehorizons.erp.controller.event.EventStatus;
import com.littlehorizons.erp.controller.event.StudentEventHistory;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.EventPosition;
import com.littlehorizons.erp.model.enums.EventType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Manual check of module 2.2 (plain Java). It creates its own teachers (documents starting with
 * 9960), students (9961) and guardians (9962) and events named «Evento Zzprueba ...», and deletes
 * everything at the end. The last checks read the sample data of docs/database.sql. Run main()
 * from IntelliJ with the database loaded and config.txt in place.
 */
public final class EventModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        TeacherController teachers = new TeacherController(daos.teacherDao(), logger);
        EventController events = new EventController(daos.eventDao(), logger);
        EventParticipationController participations = new EventParticipationController(
                daos.eventParticipationDao(), daos.eventDao(), logger);
        LocalDate today = LocalDate.now();

        try {
            cleanUp();

            // ---- enums, entities and the pure calculations ----
            check("The enums mirror the database labels", () ->
                    EventType.values().length == 4
                            && EventType.fromDbLabel("Académico") == EventType.ACADEMIC
                            && EventType.fromDbLabel("Deportivo") == EventType.SPORTS
                            && EventPosition.fromDbLabel("1er Lugar") == EventPosition.FIRST
                            && EventPosition.fromDbLabel("Participante") == EventPosition.PARTICIPANT
                            && EventPosition.FIRST.isPodium() && !EventPosition.PARTICIPANT.isPodium());
            Teacher probeTeacher = teacher("99600000009", "Probe", "probe.event@test.edu");
            check("An event needs a letters-only name, a description, a type, a valid date and an organizer", () ->
                    throwsValidation(() -> new SchoolEvent("Feria 2026", "Algo", EventType.ACADEMIC, today, null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("  ", "Algo", EventType.ACADEMIC, today, null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", " ", EventType.ACADEMIC, today, null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", "x".repeat(501), EventType.ACADEMIC, today, null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, null, null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, today.plusYears(6), null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, LocalDate.of(1999, 1, 1), null, false, probeTeacher))
                            && throwsValidation(() -> new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, today, "Sala 3", false, probeTeacher))
                            && new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, today.minusYears(1), "Auditorio", false, probeTeacher).getPlace().equals("Auditorio")
                            && new SchoolEvent("Evento Zzprueba", "Algo", EventType.ACADEMIC, today, "  ", false, probeTeacher).getPlace() == null);
            Student probeStudent = student("99610000009", "Probe", "Prueba");
            check("A participation only gets a position when the event has a competition", () -> {
                SchoolEvent contest = event("Evento Zzprueba Prueba", today, true, probeTeacher);
                SchoolEvent fair = event("Evento Zzprueba Fiesta", today, false, probeTeacher);
                EventParticipation inContest = new EventParticipation(contest, probeStudent);
                EventParticipation inFair = new EventParticipation(fair, probeStudent);
                inContest.assignPosition(EventPosition.SECOND);
                return inContest.getPosition() == EventPosition.SECOND
                        && new EventParticipation(contest, probeStudent).getPosition() == EventPosition.PARTICIPANT
                        && inFair.getPosition() == null
                        && throwsValidation(() -> inFair.assignPosition(EventPosition.FIRST))
                        && throwsValidation(() -> inContest.assignPosition(null))
                        && inContest.getPosition() == EventPosition.SECOND;
            });
            check("The status depends on the date and on the cancelled flag", () -> {
                SchoolEvent past = event("Evento Zzprueba Pasado", today.minusDays(3), false, probeTeacher);
                SchoolEvent now = event("Evento Zzprueba Hoy", today, false, probeTeacher);
                SchoolEvent next = event("Evento Zzprueba Futuro", today.plusDays(3), false, probeTeacher);
                SchoolEvent cancelled = event("Evento Zzprueba Cancelado", today.plusDays(3), false, probeTeacher);
                cancelled.setActive(false);
                return EventAnalyzer.statusOf(past, today) == EventStatus.HELD
                        && EventAnalyzer.statusOf(now, today) == EventStatus.TODAY
                        && EventAnalyzer.statusOf(next, today) == EventStatus.UPCOMING
                        && EventAnalyzer.statusOf(cancelled, today) == EventStatus.CANCELLED;
            });
            check("The calendar is ordered (upcoming first, then held newest first, cancelled last) and totalled", () -> {
                SchoolEvent a = event("Evento Zzprueba Zeta", today.plusDays(10), true, probeTeacher, 1);
                SchoolEvent b = event("Evento Zzprueba Alfa", today.plusDays(2), false, probeTeacher, 2);
                SchoolEvent c = event("Evento Zzprueba Viejo", today.minusDays(30), false, probeTeacher, 3);
                SchoolEvent d = event("Evento Zzprueba Reciente", today.minusDays(2), true, probeTeacher, 4);
                SchoolEvent e = event("Evento Zzprueba Anulado", today.plusDays(1), false, probeTeacher, 5);
                e.setActive(false);
                EventCalendar calendar = EventAnalyzer.calendar(List.of(a, c, e, d, b), Map.of(1, 0, 3, 5, 4, 7), today);
                List<Integer> ids = calendar.entries().stream().map(x -> x.event().getId()).toList();
                var s = calendar.summary();
                return ids.equals(List.of(2, 1, 4, 3, 5))
                        && s.total() == 5 && s.upcoming() == 2 && s.held() == 2 && s.cancelled() == 1
                        && s.participations() == 12
                        && s.byType().get(EventType.ACADEMIC) == 4L && s.byType().size() == 1;
            });
            check("The results are sorted by position and then by last name (Spanish), with counts per position", () -> {
                SchoolEvent contest = event("Evento Zzprueba Concurso", today, true, probeTeacher, 1);
                EventParticipation p1 = part(contest, student("99610000011", "Zoe", "Álvarez"), EventPosition.PARTICIPANT);
                EventParticipation p2 = part(contest, student("99610000012", "Ana", "Zapata"), EventPosition.FIRST);
                EventParticipation p3 = part(contest, student("99610000013", "Eva", "Ángel"), EventPosition.PARTICIPANT);
                EventParticipation p4 = part(contest, student("99610000014", "Mia", "Bravo"), EventPosition.THIRD);
                EventResults r = EventAnalyzer.results(contest, List.of(p1, p3, p4, p2));
                return r.participants().equals(List.of(p2, p4, p1, p3))
                        && r.positionCounts().get(EventPosition.PARTICIPANT) == 2L
                        && r.positionCounts().get(EventPosition.FIRST) == 1L
                        && !r.positionCounts().containsKey(EventPosition.SECOND);
            });
            check("The student history is newest first and counts the podium places", () -> {
                SchoolEvent old = event("Evento Zzprueba Antiguo", today.minusDays(90), true, probeTeacher, 1);
                SchoolEvent mid = event("Evento Zzprueba Medio", today.minusDays(30), false, probeTeacher, 2);
                SchoolEvent recent = event("Evento Zzprueba Nuevo", today.minusDays(5), true, probeTeacher, 3);
                StudentEventHistory h = EventAnalyzer.history(List.of(
                        part(old, probeStudent, EventPosition.SECOND), part(recent, probeStudent, EventPosition.PARTICIPANT),
                        new EventParticipation(mid, probeStudent)));
                return h.participations().stream().map(p -> p.getEvent().getId()).toList().equals(List.of(3, 2, 1))
                        && h.podiums() == 1;
            });

            // ---- setup ----
            Teacher ana = teacher("99600000001", "Ana", "ana.event@test.edu");
            Teacher luis = teacher("99600000002", "Luis", "luis.event@test.edu");
            check("Register two teachers; one goes on leave", () -> teachers.register(ana).isSuccess()
                    && teachers.register(luis).isSuccess() && teachers.changeStatus(luis, TeacherStatus.ON_LEAVE).isSuccess());
            List<Student> pupils = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                Student s = student("9961000000" + i, "Alumno" + (char) ('A' + i), "Prueba");
                pupils.add(s);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "9962000000" + i, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
            }
            check("Register six students", () -> pupils.stream().allMatch(s -> s.getId() != null));
            Student retired = pupils.get(5);
            check("One student is retired", () -> students.retire(retired).isSuccess());

            // ---- events ----
            SchoolEvent science = new SchoolEvent("Evento Zzprueba Ciencias", "Proyectos científicos", EventType.ACADEMIC,
                    today.minusDays(7), "Coliseo", true, ana);
            check("Create an event with competition", () -> events.create(science).isSuccess() && science.getId() != null);
            check("The same name on the same date cannot be repeated (even in other case)", () -> {
                Result<SchoolEvent> r = events.create(new SchoolEvent("evento zzprueba ciencias", "Otro", EventType.CULTURAL,
                        today.minusDays(7), null, false, ana));
                return !r.isSuccess() && r.getMessage().contains("Ya existe");
            });
            check("The same name on another date is allowed (the Science Fair is yearly)", () ->
                    events.create(new SchoolEvent("Evento Zzprueba Ciencias", "Edición futura", EventType.ACADEMIC,
                            today.plusDays(40), "Coliseo", true, ana)).isSuccess());
            check("The check tells whether a name+date is taken (and ignores the event being edited)", () ->
                    events.nameAndDateTaken("Evento Zzprueba Ciencias", today.minusDays(7), null).getData()
                            && !events.nameAndDateTaken("Evento Zzprueba Ciencias", today.minusDays(7), science.getId()).getData()
                            && !events.nameAndDateTaken("Evento Zzprueba Ciencias", today.plusDays(1), null).getData());
            check("The organizer must be an active teacher", () -> {
                Result<SchoolEvent> r = events.create(new SchoolEvent("Evento Zzprueba Licencia", "Algo", EventType.SOCIAL,
                        today.plusDays(5), null, false, luis));
                return !r.isSuccess() && r.getMessage().contains("activo");
            });
            SchoolEvent culture = new SchoolEvent("Evento Zzprueba Cultural", "Muestra de danza", EventType.CULTURAL,
                    today.minusDays(2), "Auditorio", false, ana);
            SchoolEvent upcoming = new SchoolEvent("Evento Zzprueba Proximo", "Torneo futuro", EventType.SPORTS,
                    today.plusDays(10), null, true, ana);
            check("Create an event without competition and an upcoming one", () ->
                    events.create(culture).isSuccess() && events.create(upcoming).isSuccess());
            check("Search finds events by name, place and organizer", () ->
                    events.search("zzprueba cultural").getData().size() == 1
                            && events.search("coliseo").getData().stream().anyMatch(e -> e.getId().equals(science.getId()))
                            && events.search("Ana").getData().size() >= 3
                            && !events.search("   ").isSuccess());
            check("Search for competitions skips events without one", () ->
                    events.searchCompetitions("Evento Zzprueba").getData().stream().allMatch(SchoolEvent::hasCompetition)
                            && events.searchCompetitions("Evento Zzprueba").getData().size() == 3);

            // ---- participants ----
            check("Register a participant in an event with competition (starts as Participante)", () -> {
                Result<EventParticipation> r = participations.register(pupils.get(0), science, null);
                return r.isSuccess() && r.getData().getPosition() == EventPosition.PARTICIPANT && r.getData().getId() != null;
            });
            check("A student cannot be registered twice in the same event", () -> {
                Result<EventParticipation> r = participations.register(pupils.get(0), science, null);
                return !r.isSuccess() && r.getMessage().contains("ya está registrado");
            });
            check("Register with the position directly", () -> {
                Result<EventParticipation> r = participations.register(pupils.get(1), science, EventPosition.FIRST);
                return r.isSuccess() && r.getData().getPosition() == EventPosition.FIRST;
            });
            check("A retired student cannot be registered", () -> {
                Result<EventParticipation> r = participations.register(retired, science, null);
                return !r.isSuccess() && r.getMessage().contains("activos");
            });
            check("An upcoming event does not take participants yet", () -> {
                Result<EventParticipation> r = participations.register(pupils.get(0), upcoming, null);
                return !r.isSuccess() && r.getMessage().contains("ya llegó");
            });
            check("An event without competition takes participants but not positions", () -> {
                Result<EventParticipation> ok = participations.register(pupils.get(0), culture, null);
                Result<EventParticipation> bad = participations.register(pupils.get(1), culture, EventPosition.FIRST);
                return ok.isSuccess() && ok.getData().getPosition() == null
                        && !bad.isSuccess() && bad.getMessage().contains("no tiene competencia")
                        && !participations.assignPosition(ok.getData(), EventPosition.FIRST).isSuccess()
                        && daos.eventParticipationDao().findByEvent(culture.getId()).size() == 1;
            });
            EventParticipation[] second = new EventParticipation[1];
            check("Assign positions: several students can share one (ties and team events)", () -> {
                second[0] = participations.register(pupils.get(2), science, null).getData();
                return participations.assignPosition(second[0], EventPosition.SECOND).isSuccess()
                        && participations.register(pupils.get(3), science, EventPosition.SECOND).isSuccess()
                        && scalar("SELECT COUNT(*) FROM participantes_evento WHERE id_evento = " + science.getId()
                        + " AND posicion_obtenida = '2do Lugar'") == 2;
            });
            check("The position is saved in the database and read back", () ->
                    daos.eventParticipationDao().findByEvent(science.getId()).stream()
                            .filter(p -> p.getStudent().getId().equals(pupils.get(2).getId()))
                            .findFirst().orElseThrow().getPosition() == EventPosition.SECOND);
            check("Results of the event: ordered, with counts", () -> {
                EventResults r = participations.results(science).getData();
                return r.participants().size() == 4
                        && r.participants().get(0).getPosition() == EventPosition.FIRST
                        && r.participants().get(3).getPosition() == EventPosition.PARTICIPANT
                        && r.positionCounts().get(EventPosition.SECOND) == 2L
                        && r.participants().get(0).getEvent().getOrganizer().getFullName().contains("Ana")
                        && r.participants().get(0).getEvent().getOrganizer().getStatus() == TeacherStatus.ACTIVE;
            });
            check("The history of a student lists his or her events with positions", () -> {
                StudentEventHistory h = participations.historyOf(pupils.get(1)).getData();
                return h.participations().size() == 1 && h.podiums() == 1
                        && h.participations().get(0).getEvent().getName().equals("Evento Zzprueba Ciencias")
                        && participations.historyOf(pupils.get(4)).getData().participations().isEmpty()
                        && participations.historyOf(pupils.get(0)).getData().participations().size() == 2;
            });
            check("A participant can be removed", () -> {
                EventParticipation p = participations.register(pupils.get(4), culture, null).getData();
                return participations.remove(p).isSuccess()
                        && !daos.eventParticipationDao().exists(culture.getId(), pupils.get(4).getId());
            });

            // ---- editing and cancelling ----
            check("A competition with podium places cannot be turned into a normal event", () -> {
                SchoolEvent changed = copy(science, false, true);
                Result<SchoolEvent> r = events.update(changed);
                return !r.isSuccess() && r.getMessage().contains("podio")
                        && daos.eventDao().findById(science.getId()).orElseThrow().hasCompetition();
            });
            check("Turning an event without competition into one with competition sets every participant to Participante", () -> {
                Result<SchoolEvent> r = events.update(copy(culture, true, true));
                return r.isSuccess()
                        && participations.results(culture).getData().participants().stream()
                        .allMatch(p -> p.getPosition() == EventPosition.PARTICIPANT);
            });
            check("Turning it back (nobody on the podium) clears the positions", () -> {
                Result<SchoolEvent> r = events.update(copy(culture, false, true));
                return r.isSuccess()
                        && scalar("SELECT COUNT(*) FROM participantes_evento WHERE id_evento = " + culture.getId()
                        + " AND posicion_obtenida IS NOT NULL") == 0;
            });
            check("An edit can change name, type, place and date, and the database keeps it", () -> {
                SchoolEvent changed = new SchoolEvent("Evento Zzprueba Cultural Nuevo", "Danza y música", EventType.SOCIAL,
                        today.minusDays(1), "Patio", false, ana);
                changed.setId(culture.getId());
                SchoolEvent saved = null;
                if (events.update(changed).isSuccess()) {
                    saved = daos.eventDao().findById(culture.getId()).orElseThrow();
                }
                return saved != null && saved.getName().equals("Evento Zzprueba Cultural Nuevo")
                        && saved.getType() == EventType.SOCIAL && saved.getPlace().equals("Patio")
                        && saved.getDate().equals(today.minusDays(1));
            });
            check("An edit cannot collide with another event's name and date", () -> {
                SchoolEvent clash = copy(culture, false, true);
                clash.setName("Evento Zzprueba Ciencias");
                clash.setDate(science.getDate());
                Result<SchoolEvent> r = events.update(clash);
                return !r.isSuccess() && r.getMessage().contains("Ya existe");
            });
            check("An event with participants cannot be cancelled", () -> {
                Result<SchoolEvent> r = events.setActive(science, false);
                return !r.isSuccess() && r.getMessage().contains("participante") && science.isActive();
            });
            check("An event without participants can be cancelled, then no participants are accepted, and it can be restored", () -> {
                Result<SchoolEvent> off = events.setActive(upcoming, false);
                boolean cancelled = off.isSuccess() && !upcoming.isActive()
                        && !daos.eventDao().findById(upcoming.getId()).orElseThrow().isActive();
                SchoolEvent pastCancelled = new SchoolEvent("Evento Zzprueba Anulado", "Se canceló", EventType.SOCIAL,
                        today.minusDays(4), null, false, ana);
                events.create(pastCancelled);
                events.setActive(pastCancelled, false);
                Result<EventParticipation> refused = participations.register(pupils.get(0), pastCancelled, null);
                Result<SchoolEvent> on = events.setActive(upcoming, true);
                return cancelled && !refused.isSuccess() && refused.getMessage().contains("cancelado")
                        && on.isSuccess() && upcoming.isActive()
                        && !events.setActive(upcoming, true).isSuccess();
            });
            check("The calendar of the database shows my events with their status and participants", () -> {
                EventCalendar calendar = events.calendar().getData();
                EventCalendarEntry entry = calendar.entries().stream()
                        .filter(e -> e.event().getId().equals(science.getId())).findFirst().orElseThrow();
                return entry.participants() == 4 && entry.status() == EventStatus.HELD
                        && calendar.summary().total() == calendar.entries().size();
            });
            check("Deleting a student removes his or her participations (database cascade)", () -> {
                Student temp = student("99610000050", "Temporal", "Prueba");
                students.register(temp, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "99620000050", "Madre", "Prueba", "Madre", "3001234567"), true, true))));
                participations.register(temp, science, null);
                long before = scalar("SELECT COUNT(*) FROM participantes_evento WHERE id_evento = " + science.getId());
                execute("DELETE FROM estudiantes WHERE numero_documento = '99610000050'");
                return scalar("SELECT COUNT(*) FROM participantes_evento WHERE id_evento = " + science.getId()) == before - 1;
            });
            check("Nothing in the database has a position in an event without competition (the Java rules keep it coherent)", () ->
                    scalar("SELECT COUNT(*) FROM participantes_evento p JOIN eventos e ON e.id_evento = p.id_evento "
                            + "WHERE e.tiene_competencia = 0 AND p.posicion_obtenida IS NOT NULL") == 0);

            // ---- the sample data of docs/database.sql ----
            check("The sample data has at least 20 events of the four types and 100 participations", () ->
                    scalar("SELECT COUNT(*) FROM eventos WHERE nombre_evento NOT LIKE 'Evento Zzprueba%'") >= 20
                            && scalar("SELECT COUNT(DISTINCT tipo_evento) FROM eventos WHERE nombre_evento NOT LIKE 'Evento Zzprueba%'") == 4
                            && scalar("SELECT COUNT(*) FROM participantes_evento WHERE id_estudiante NOT IN (SELECT id_estudiante FROM estudiantes WHERE numero_documento LIKE '9961%')") >= 100);
            check("Sample: every competition participant has a position and no other event has positions", () ->
                    scalar("SELECT COUNT(*) FROM participantes_evento p JOIN eventos e ON e.id_evento = p.id_evento "
                            + "WHERE e.nombre_evento NOT LIKE 'Evento Zzprueba%' AND ((e.tiene_competencia = 1 AND p.posicion_obtenida IS NULL) "
                            + "OR (e.tiene_competencia = 0 AND p.posicion_obtenida IS NOT NULL))") == 0);
            check("Sample: «Feria de Ciencias» 2026 has David Arango as 1er Lugar", () -> {
                SchoolEvent fair = events.search("Feria de Ciencias").getData().stream()
                        .filter(e -> e.getDate().getYear() == 2026).findFirst().orElseThrow();
                EventResults r = participations.results(fair).getData();
                return fair.hasCompetition() && r.participants().get(0).getPosition() == EventPosition.FIRST
                        && r.participants().get(0).getStudent().getDocumentNumber().equals("1097258444");
            });
            check("Sample: «Torneo de Voleibol» has only Participante (positions pending), «Feria del Libro» has nobody", () -> {
                SchoolEvent volley = events.search("Torneo de Voleibol").getData().get(0);
                SchoolEvent book = events.search("Feria del Libro").getData().get(0);
                return participations.results(volley).getData().participants().stream()
                        .allMatch(p -> p.getPosition() == EventPosition.PARTICIPANT)
                        && participations.results(book).getData().participants().isEmpty();
            });
            check("Sample: «Caminata Ecológica» is cancelled and the calendar totals add up", () -> {
                EventCalendar calendar = events.calendar().getData();
                var s = calendar.summary();
                return calendar.entries().stream().anyMatch(e -> e.event().getName().equals("Caminata Ecológica")
                        && e.status() == EventStatus.CANCELLED)
                        && s.cancelled() >= 1 && s.upcoming() + s.held() + s.cancelled() == s.total()
                        && s.byType().size() == 4;
            });
            check("Sample: David Arango took part in several events and has a podium place", () -> {
                StudentEventHistory h = participations.historyOf(daos.studentDao().findByDocumentNumber("1097258444").orElseThrow()).getData();
                return h.participations().size() >= 4 && h.podiums() >= 1;
            });
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Student student(String document, String firstName, String lastName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName(lastName).gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2012, 5, 20)).admissionDate(LocalDate.now().minusYears(1)).build();
    }

    private static Teacher teacher(String document, String firstName, String email) {
        return new Teacher(DocumentType.CC, document, firstName, "Prueba", email, "Matemáticas", LocalDate.of(2020, 1, 1));
    }

    private static SchoolEvent event(String name, LocalDate date, boolean competition, Teacher organizer) {
        return new SchoolEvent(name, "Algo", EventType.ACADEMIC, date, null, competition, organizer);
    }

    private static SchoolEvent event(String name, LocalDate date, boolean competition, Teacher organizer, int id) {
        SchoolEvent event = event(name, date, competition, organizer);
        event.setId(id);
        return event;
    }

    private static EventParticipation part(SchoolEvent event, Student student, EventPosition position) {
        EventParticipation participation = new EventParticipation(event, student);
        if (event.hasCompetition()) {
            participation.assignPosition(position);
        }
        return participation;
    }

    /** A copy of the event with another competition flag (same id, data and state), as the edit form builds it. */
    private static SchoolEvent copy(SchoolEvent event, boolean competition, boolean active) {
        SchoolEvent copy = new SchoolEvent(event.getName(), event.getDescription(), event.getType(), event.getDate(),
                event.getPlace(), competition, event.getOrganizer());
        copy.setId(event.getId());
        copy.setActive(active);
        return copy;
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

    /** Deletes what this check created. The events first: they refer to the teachers (participations go with them). */
    private static void cleanUp() throws SQLException, DaoException {
        execute("DELETE FROM eventos WHERE nombre_evento LIKE 'Evento Zzprueba%'");
        execute("DELETE FROM estudiantes WHERE numero_documento LIKE '9961%'");
        execute("DELETE FROM acudientes WHERE numero_documento LIKE '9962%'");
        execute("DELETE FROM profesores WHERE numero_documento LIKE '9960%'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
