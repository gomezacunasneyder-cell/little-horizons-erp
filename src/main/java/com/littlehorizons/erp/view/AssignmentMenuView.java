package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.ScheduleController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.TeacherWorkload;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.ScheduleSlot;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.Weekday;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Subjects assigned to teachers, their class schedules and their academic load. */
public final class AssignmentMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Asignar una materia a un profesor",
            "Ver asignaciones de un profesor",
            "Ver profesores de una materia",
            "Quitar una asignación",
            "Agregar un horario a una asignación",
            "Ver horario semanal de un profesor",
            "Quitar un bloque de horario",
            "Ver horario de un grupo (grado y sección)",
            "Carga académica de un profesor");

    private final AssignmentController assignments;
    private final ScheduleController schedules;
    private final EntitySelector<Teacher> teacherSelector;
    private final ResultPrinter printer;

    public AssignmentMenuView(ConsoleInput input, ConsoleOutput output, AssignmentController assignments,
                              ScheduleController schedules, TeacherController teachers) {
        super(input, output);
        this.assignments = assignments;
        this.schedules = schedules;
        this.teacherSelector = TeacherMenuView.teacherSelector(input, output, teachers);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "ASIGNACIONES, HORARIOS Y CARGA ACADÉMICA";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> assign();
            case 2 -> showAssignments();
            case 3 -> showTeachersOfSubject();
            case 4 -> removeAssignment();
            case 5 -> addSlot();
            case 6 -> showTeacherWeek();
            case 7 -> removeSlot();
            case 8 -> showGroupWeek();
            case 9 -> showWorkload();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    // ---- assignments ----

    private void assign() {
        Teacher teacher = teacherSelector.select();
        Subject subject = readSubject();
        if (subject == null) {
            return;
        }
        int year = readYear();
        String section = input.readMatching("Sección o grupo (ejemplo: A): ",
                TeachingAssignment.SECTION_PATTERN, TeachingAssignment.SECTION_MESSAGE);
        printer.report(assignments.assign(teacher, subject, year, section));
    }

    private void showAssignments() {
        Teacher teacher = teacherSelector.select();
        int year = readYear();
        Result<List<TeachingAssignment>> result = assignments.byTeacher(teacher, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Materias de " + teacher.getFullName() + " en " + year);
        output.table(List.of("Grupo", "Código", "Materia"),
                result.getData().stream()
                        .map(a -> List.of(a.getGroupName(), a.getSubject().getCode(), a.getSubject().getName()))
                        .toList());
    }

    private void showTeachersOfSubject() {
        Subject subject = readSubject();
        if (subject == null) {
            return;
        }
        int year = readYear();
        Result<List<TeachingAssignment>> result = assignments.bySubject(subject, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Profesores de " + subject + " en " + year);
        output.table(List.of("Sección", "Profesor", "Especialización"),
                result.getData().stream()
                        .map(a -> List.of(a.getSection(), a.getTeacher().getFullName(), a.getTeacher().getSpecialization()))
                        .toList());
    }

    private void removeAssignment() {
        TeachingAssignment assignment = chooseAssignment();
        if (assignment == null) {
            return;
        }
        output.info("Al quitar la asignación también se eliminan sus bloques de horario.");
        if (input.readYesNo("¿Quitar " + assignment + "?")) {
            printer.report(assignments.remove(assignment));
        } else {
            output.info("No se realizaron cambios.");
        }
    }

    // ---- schedules ----

    private void addSlot() {
        TeachingAssignment assignment = chooseAssignment();
        if (assignment == null) {
            return;
        }
        Weekday day = input.readOption("Día: ", List.of(Weekday.values()), Weekday::getDisplayName);
        LocalTime start = input.readTime("Hora de inicio (HH:mm): ");
        LocalTime end = input.readTime("Hora de fin (HH:mm): ");
        printer.report(schedules.addSlot(assignment, day, start, end));
    }

    private void showTeacherWeek() {
        Teacher teacher = teacherSelector.select();
        int year = readYear();
        printWeek("Horario de " + teacher.getFullName() + " en " + year,
                schedules.weeklyScheduleOf(teacher, year),
                List.of("Día", "Hora", "Materia", "Grupo"),
                slot -> List.of(slot.getDay().getDisplayName(), slot.getStart() + "-" + slot.getEnd(),
                        slot.getAssignment().getSubject().getName(), slot.getAssignment().getGroupName()));
    }

    private void showGroupWeek() {
        GradeLevel grade = readGrade();
        if (grade == null) {
            return;
        }
        String section = input.readMatching("Sección o grupo (ejemplo: A): ",
                TeachingAssignment.SECTION_PATTERN, TeachingAssignment.SECTION_MESSAGE);
        int year = readYear();
        printWeek("Horario de " + grade.getName() + " " + section.toUpperCase() + " en " + year,
                schedules.weeklyScheduleOfGroup(grade, section, year),
                List.of("Día", "Hora", "Materia", "Profesor"),
                slot -> List.of(slot.getDay().getDisplayName(), slot.getStart() + "-" + slot.getEnd(),
                        slot.getAssignment().getSubject().getName(), slot.getAssignment().getTeacher().getFullName()));
    }

    private void removeSlot() {
        TeachingAssignment assignment = chooseAssignment();
        if (assignment == null) {
            return;
        }
        Result<List<ScheduleSlot>> slots = schedules.slotsOf(assignment);
        if (!slots.isSuccess()) {
            output.error(slots.getMessage());
            return;
        }
        if (slots.getData().isEmpty()) {
            output.info("Esa asignación aún no tiene horario.");
            return;
        }
        ScheduleSlot slot = input.readOption("Bloque a quitar: ", slots.getData(), ScheduleSlot::getTimeText);
        if (input.readYesNo("¿Quitar el bloque " + slot.getTimeText() + "?")) {
            printer.report(schedules.removeSlot(slot));
        } else {
            output.info("No se realizaron cambios.");
        }
    }

    private void showWorkload() {
        Teacher teacher = teacherSelector.select();
        int year = readYear();
        Result<TeacherWorkload> result = assignments.workload(teacher, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        TeacherWorkload workload = result.getData();
        output.info("Carga académica de " + teacher.getFullName() + " en " + year);
        output.table(List.of("Grupo", "Materia", "Horas/semana"),
                workload.lines().stream()
                        .map(line -> List.of(line.assignment().getGroupName(), line.assignment().getSubject().getName(),
                                String.format("%.1f", line.weeklyHours())))
                        .toList());
        output.line("Materias asignadas: " + workload.lines().size()
                + "   |   Total horas/semana: " + String.format("%.1f", workload.totalWeeklyHours()));
    }

    // ---- shared helpers ----

    private void printWeek(String title, Result<Map<Weekday, List<ScheduleSlot>>> result,
                           List<String> headers, Function<ScheduleSlot, List<String>> row) {
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info(title);
        output.table(headers, result.getData().values().stream()
                .flatMap(List::stream)
                .map(row)
                .toList());
    }

    private int readYear() {
        int current = LocalDate.now().getYear();
        return input.read("Año académico (Enter = " + current + "): ", text -> {
            if (text.isEmpty()) {
                return current;
            }
            int year = Integer.parseInt(text);
            if (year < Enrollment.MIN_YEAR || year > current + 1) {
                throw new ValidationException("El año debe estar entre " + Enrollment.MIN_YEAR + " y " + (current + 1) + ".");
            }
            return year;
        });
    }

    private GradeLevel readGrade() {
        Result<List<GradeLevel>> grades = assignments.grades();
        if (!grades.isSuccess()) {
            output.error(grades.getMessage());
            return null;
        }
        return input.readOption("Grado: ", grades.getData(), GradeLevel::getName);
    }

    /** Picks a grade and then one of its subjects; null if something could not be loaded. */
    private Subject readSubject() {
        GradeLevel grade = readGrade();
        if (grade == null) {
            return null;
        }
        Result<List<Subject>> subjects = assignments.subjectsOf(grade);
        if (!subjects.isSuccess()) {
            output.error(subjects.getMessage());
            return null;
        }
        if (subjects.getData().isEmpty()) {
            output.info("Ese grado no tiene materias activas.");
            return null;
        }
        return input.readOption("Materia: ", subjects.getData(), Subject::toString);
    }

    /** Picks a teacher, a year and one of his assignments; null if he has none. */
    private TeachingAssignment chooseAssignment() {
        Teacher teacher = teacherSelector.select();
        int year = readYear();
        Result<List<TeachingAssignment>> result = assignments.byTeacher(teacher, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return null;
        }
        if (result.getData().isEmpty()) {
            output.info(teacher.getFullName() + " no tiene asignaciones en " + year + ".");
            return null;
        }
        return input.readOption("Asignación: ", result.getData(), TeachingAssignment::toString);
    }
}
