package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.Weekday;

import java.time.LocalTime;
import java.util.Arrays;

/** Asks the data of a new club, or the changes of an existing one, until every answer is valid. */
final class ClubForm {

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final ClubController controller;
    private final EntitySelector<Teacher> teacherSelector;

    ClubForm(ConsoleInput input, ConsoleOutput output, ClubController controller,
             EntitySelector<Teacher> teacherSelector) {
        this.input = input;
        this.output = output;
        this.controller = controller;
        this.teacherSelector = teacherSelector;
    }

    Club readNew() {
        String name = input.read("Nombre del club (solo letras, ejemplo: Club de Ciencias): ", text -> checkName(text, null));
        String description = input.read("Descripción (qué hacen en el club): ", Club::checkDescription);
        Teacher supervisor = readSupervisor();
        int capacity = input.readInt("Cupo máximo (" + Club.MIN_CAPACITY + "-" + Club.MAX_CAPACITY + "): ",
                Club.MIN_CAPACITY, Club.MAX_CAPACITY);
        String schedule = readSchedule();
        return new Club(name, description, supervisor, capacity, schedule);
    }

    /**
     * Returns a NEW Club with the changes (same id and state). The original is not touched, so if the
     * user cancels in the middle or the controller rejects the change, nothing is half-edited.
     */
    Club readChanges(Club current) {
        String name = input.read("Nombre [" + current.getName() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getName() : checkName(text, current.getId()));
        String description = input.read("Descripción [" + current.getDescription() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getDescription() : Club.checkDescription(text));
        Teacher supervisor = input.readYesNo("¿Cambiar el profesor encargado (" + current.getSupervisor().getFullName() + ")?")
                ? readSupervisor() : current.getSupervisor();
        int capacity = input.read("Cupo máximo [" + current.getMaxCapacity() + "] (Enter = mantener): ", text -> {
            if (text.isEmpty()) {
                return current.getMaxCapacity();
            }
            int value = Integer.parseInt(text);
            if (value < Club.MIN_CAPACITY || value > Club.MAX_CAPACITY) {
                throw new ValidationException("El cupo debe estar entre " + Club.MIN_CAPACITY + " y " + Club.MAX_CAPACITY + ".");
            }
            return value;
        });
        String schedule = input.readYesNo("¿Cambiar el horario (" + current.getMeetingSchedule() + ")?")
                ? readSchedule() : current.getMeetingSchedule();
        Club changed = new Club(name, description, supervisor, capacity, schedule);
        changed.setId(current.getId());
        changed.setActive(current.isActive());
        return changed;
    }

    private String checkName(String text, Integer excludeId) {
        String name = Person.requireLabel(text, Club.NAME_MAX, "El nombre del club");
        Result<Boolean> taken = controller.nameTaken(name, excludeId);
        if (taken.isSuccess() && taken.getData()) {
            throw new ValidationException("Ya existe un club con el nombre «" + name + "».");
        }
        return name;
    }

    /** Asks for a teacher until an ACTIVE one is chosen. */
    private Teacher readSupervisor() {
        while (true) {
            Teacher teacher = teacherSelector.select();
            if (teacher.isActive()) {
                return teacher;
            }
            output.error(teacher.getFullName() + " está " + teacher.getStatus().getDisplayName().toLowerCase()
                    + ". Elija un profesor activo.");
        }
    }

    private String readSchedule() {
        Weekday day = input.readOption("Día de reunión: ", Arrays.asList(Weekday.values()), Weekday::getDisplayName);
        LocalTime start = input.readTime("Hora de inicio (HH:mm, ejemplo 15:00): ");
        while (true) {
            LocalTime end = input.readTime("Hora de fin (HH:mm, ejemplo 17:00): ");
            try {
                return Club.formatSchedule(day, start, end);
            } catch (ValidationException e) {
                output.error(e.getMessage());
            }
        }
    }
}
