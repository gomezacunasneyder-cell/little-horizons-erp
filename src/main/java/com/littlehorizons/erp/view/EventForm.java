package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.EventType;

import java.time.LocalDate;
import java.util.Arrays;

/** Asks the data of a new event, or the changes of an existing one, until every answer is valid. */
final class EventForm {

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final EventController controller;
    private final EntitySelector<Teacher> teacherSelector;

    EventForm(ConsoleInput input, ConsoleOutput output, EventController controller,
              EntitySelector<Teacher> teacherSelector) {
        this.input = input;
        this.output = output;
        this.controller = controller;
        this.teacherSelector = teacherSelector;
    }

    SchoolEvent readNew() {
        String name = input.read("Nombre del evento (solo letras, ejemplo: Feria de Ciencias): ",
                text -> Person.requireLabel(text, SchoolEvent.NAME_MAX, "El nombre del evento"));
        LocalDate date = readDate("Fecha del evento (aaaa-mm-dd): ");
        // the name and the date together identify the event, so the duplicate check happens here
        while (isTaken(name, date, null)) {
            output.error("Ya existe el evento «" + name + "» el " + date + ". Escriba otra fecha.");
            date = readDate("Fecha del evento (aaaa-mm-dd): ");
        }
        String description = input.read("Descripción (de qué trata el evento): ", SchoolEvent::checkDescription);
        EventType type = readType();
        String place = input.read("Lugar (Enter para omitir): ",
                text -> Person.optionalLabel(text, SchoolEvent.PLACE_MAX, "El lugar"));
        boolean competition = input.readYesNo("¿Tiene competencia con posiciones (1er, 2do, 3er lugar)?");
        Teacher organizer = readOrganizer();
        return new SchoolEvent(name, description, type, date, place, competition, organizer);
    }

    /**
     * Returns a NEW SchoolEvent with the changes (same id and state). The original is not touched, so
     * if the user cancels in the middle or the controller rejects the change, nothing is half-edited.
     */
    SchoolEvent readChanges(SchoolEvent current) {
        String name = input.read("Nombre [" + current.getName() + "] (Enter = mantener): ", text -> text.isEmpty()
                ? current.getName() : Person.requireLabel(text, SchoolEvent.NAME_MAX, "El nombre del evento"));
        LocalDate date = input.read("Fecha [" + current.getDate() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getDate() : SchoolEvent.checkDate(LocalDate.parse(text)));
        while (isTaken(name, date, current.getId())) {
            output.error("Ya existe el evento «" + name + "» el " + date + ". Escriba otra fecha.");
            date = readDate("Fecha del evento (aaaa-mm-dd): ");
        }
        String description = input.read("Descripción [" + current.getDescription() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getDescription() : SchoolEvent.checkDescription(text));
        EventType type = input.readYesNo("¿Cambiar el tipo (" + current.getType().getDisplayName() + ")?")
                ? readType() : current.getType();
        String place = input.readYesNo("¿Cambiar el lugar (" + (current.getPlace() == null ? "sin lugar" : current.getPlace()) + ")?")
                ? input.read("Nuevo lugar (Enter para dejarlo vacío): ",
                        text -> Person.optionalLabel(text, SchoolEvent.PLACE_MAX, "El lugar"))
                : current.getPlace();
        boolean competition = input.readYesNo("¿Tiene competencia con posiciones? (hoy: " + (current.hasCompetition() ? "Sí" : "No") + ")");
        Teacher organizer = input.readYesNo("¿Cambiar el organizador (" + current.getOrganizer().getFullName() + ")?")
                ? readOrganizer() : current.getOrganizer();
        SchoolEvent changed = new SchoolEvent(name, description, type, date, place, competition, organizer);
        changed.setId(current.getId());
        changed.setActive(current.isActive());
        return changed;
    }

    private LocalDate readDate(String prompt) {
        return input.readDate(prompt, d -> true, "");
    }

    private boolean isTaken(String name, LocalDate date, Integer excludeId) {
        Result<Boolean> taken = controller.nameAndDateTaken(name, date, excludeId);
        return taken.isSuccess() && taken.getData();
    }

    private EventType readType() {
        return input.readOption("Tipo de evento: ", Arrays.asList(EventType.values()), EventType::getDisplayName);
    }

    /** Asks for a teacher until an ACTIVE one is chosen. */
    private Teacher readOrganizer() {
        while (true) {
            Teacher teacher = teacherSelector.select();
            if (teacher.isActive()) {
                return teacher;
            }
            output.error(teacher.getFullName() + " está " + teacher.getStatus().getDisplayName().toLowerCase()
                    + ". Elija un profesor activo.");
        }
    }
}
