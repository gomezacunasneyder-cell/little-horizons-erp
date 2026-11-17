package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.EventParticipationController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.event.EventCalendar;
import com.littlehorizons.erp.controller.event.EventResults;
import com.littlehorizons.erp.controller.event.StudentEventHistory;
import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.EventPosition;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * "Actividades Extracurriculares - Eventos": the calendar, registering and editing events, who
 * participated and, in competitions, the position of each participant. It only asks for data and
 * prints; the rules (active students, event already held, positions only in competitions) are in
 * the controllers.
 */
public final class EventMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Ver el calendario de eventos",
            "Registrar un evento",
            "Editar un evento",
            "Cancelar o reactivar un evento",
            "Registrar un participante",
            "Asignar la posición de un participante (competencias)",
            "Quitar un participante",
            "Ver participantes y resultados de un evento",
            "Ver los eventos de un estudiante");

    private final EventController events;
    private final EventParticipationController participations;
    private final EntitySelector<SchoolEvent> eventSelector;
    private final EntitySelector<SchoolEvent> competitionSelector;
    private final StudentSelector studentSelector;
    private final EventForm form;
    private final EventPrinter eventPrinter;
    private final ResultPrinter printer;

    public EventMenuView(ConsoleInput input, ConsoleOutput output, EventController events,
                         EventParticipationController participations, TeacherController teachers,
                         StudentController students) {
        super(input, output);
        this.events = events;
        this.participations = participations;
        this.eventSelector = eventSelector(input, output, "Nombre del evento (o parte de él): ", events::search);
        this.competitionSelector = eventSelector(input, output, "Nombre del evento con competencia: ", events::searchCompetitions);
        this.studentSelector = new StudentSelector(input, output, students);
        this.form = new EventForm(input, output, events, TeacherMenuView.teacherSelector(input, output, teachers));
        this.eventPrinter = new EventPrinter(output);
        this.printer = new ResultPrinter(output);
    }

    private static EntitySelector<SchoolEvent> eventSelector(ConsoleInput input, ConsoleOutput output, String prompt,
                                                             Function<String, Result<List<SchoolEvent>>> search) {
        return new EntitySelector<>(input, output, prompt, search,
                event -> event.getName() + " - " + event.getDate() + (event.isActive() ? "" : " (cancelado)"));
    }

    @Override
    protected String title() {
        return "ACTIVIDADES EXTRACURRICULARES - EVENTOS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> showCalendar();
            case 2 -> createEvent();
            case 3 -> editEvent();
            case 4 -> toggleEvent();
            case 5 -> registerParticipant();
            case 6 -> assignPosition();
            case 7 -> removeParticipant();
            case 8 -> showResults();
            case 9 -> showStudentEvents();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    void showCalendar() {
        Result<EventCalendar> result = events.calendar();
        if (result.isSuccess()) {
            eventPrinter.calendar(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void createEvent() {
        printer.report(events.create(form.readNew()));
    }

    private void editEvent() {
        SchoolEvent event = eventSelector.select();
        output.info("Datos actuales:");
        eventPrinter.detail(event);
        printer.report(events.update(form.readChanges(event)));
    }

    private void toggleEvent() {
        SchoolEvent event = eventSelector.select();
        if (event.isActive()) {
            if (input.readYesNo("¿Cancelar el evento «" + event.getName() + "» del " + event.getDate() + "?")) {
                printer.report(events.setActive(event, false));
            }
        } else if (input.readYesNo("El evento está cancelado. ¿Reactivarlo?")) {
            printer.report(events.setActive(event, true));
        }
    }

    private void registerParticipant() {
        SchoolEvent event = eventSelector.select();
        Student student = studentSelector.select();
        EventPosition position = null;
        if (event.hasCompetition()) {
            position = input.readOption("Posición obtenida: ", Arrays.asList(EventPosition.PARTICIPANT,
                    EventPosition.FIRST, EventPosition.SECOND, EventPosition.THIRD), EventPosition::getDisplayName);
        }
        printer.report(participations.register(student, event, position));
    }

    private void assignPosition() {
        EventParticipation participation = chooseParticipant(competitionSelector.select(), "Participante: ");
        if (participation == null) {
            return;
        }
        EventPosition position = input.readOption("Nueva posición: ", Arrays.asList(EventPosition.values()),
                EventPosition::getDisplayName);
        printer.report(participations.assignPosition(participation, position));
    }

    private void removeParticipant() {
        EventParticipation participation = chooseParticipant(eventSelector.select(), "Participante a quitar: ");
        if (participation == null) {
            return;
        }
        if (input.readYesNo("¿Quitar a " + participation.getStudent().getFullName() + " de «"
                + participation.getEvent().getName() + "»?")) {
            printer.report(participations.remove(participation));
        } else {
            output.info("No se quitó a nadie.");
        }
    }

    /** Lists the participants of the event and returns the one the user picks, or null if there are none. */
    private EventParticipation chooseParticipant(SchoolEvent event, String prompt) {
        Result<EventResults> result = participations.results(event);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return null;
        }
        List<EventParticipation> participants = result.getData().participants();
        if (participants.isEmpty()) {
            output.info("«" + event.getName() + "» no tiene participantes registrados.");
            return null;
        }
        return input.readOption(prompt, participants, EventPrinter::participantLabel);
    }

    private void showResults() {
        SchoolEvent event = eventSelector.select();
        Result<EventResults> result = participations.results(event);
        if (result.isSuccess()) {
            eventPrinter.results(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void showStudentEvents() {
        Student student = studentSelector.select();
        Result<StudentEventHistory> result = participations.historyOf(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else if (result.getData().participations().isEmpty()) {
            output.info(student.getFullName() + " no ha participado en ningún evento.");
        } else {
            output.info("Eventos de " + student.getFullName());
            eventPrinter.studentHistory(result.getData());
        }
    }
}
