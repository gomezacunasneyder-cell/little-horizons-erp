package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;

import java.util.List;

/** Guardian management: add, remove, change the main one, edit data and see siblings. */
public final class GuardianMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Ver acudientes de un estudiante",
            "Agregar acudiente a un estudiante",
            "Quitar acudiente de un estudiante",
            "Cambiar acudiente principal",
            "Editar datos de un acudiente",
            "Ver hermanos (estudiantes de un mismo acudiente)");

    private final StudentController controller;
    private final StudentSelector selector;
    private final StudentPrinter printer;
    private final GuardianForm form;

    public GuardianMenuView(ConsoleInput input, ConsoleOutput output, StudentController controller) {
        super(input, output);
        this.controller = controller;
        this.selector = new StudentSelector(input, output, controller);
        this.printer = new StudentPrinter(output);
        this.form = new GuardianForm(input, output, controller);
    }

    @Override
    protected String title() {
        return "ACUDIENTES";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> show(selector.select());
            case 2 -> add();
            case 3 -> remove();
            case 4 -> changePrincipal();
            case 5 -> editGuardian();
            case 6 -> siblings();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void show(Student student) {
        Result<List<StudentGuardian>> result = controller.guardiansOf(student);
        if (result.isSuccess()) {
            output.info("Acudientes de " + student.getFullName());
            printer.guardians(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void add() {
        Student student = selector.select();
        StudentGuardian link = form.readLink(false, new java.util.ArrayList<>());
        printer.report(controller.addGuardian(student, link));
    }

    private void remove() {
        Student student = selector.select();
        StudentGuardian link = chooseLink(student, "Acudiente a quitar: ");
        if (link != null && input.readYesNo("¿Quitar a " + link.getGuardian().getFullName() + "?")) {
            printer.report(controller.removeGuardian(student, link));
        }
    }

    private void changePrincipal() {
        Student student = selector.select();
        StudentGuardian link = chooseLink(student, "Nuevo acudiente principal: ");
        if (link != null) {
            printer.report(controller.changePrincipalGuardian(student, link));
        }
    }

    private void editGuardian() {
        Student student = selector.select();
        StudentGuardian link = chooseLink(student, "Acudiente a editar: ");
        if (link != null) {
            form.edit(link.getGuardian());
            printer.report(controller.updateGuardian(link.getGuardian()));
        }
    }

    private void siblings() {
        Student student = selector.select();
        Result<List<StudentGuardian>> guardians = controller.guardiansOf(student);
        if (!guardians.isSuccess()) {
            output.error(guardians.getMessage());
            return;
        }
        for (StudentGuardian link : guardians.getData()) {
            Result<List<Student>> students = controller.studentsOf(link.getGuardian());
            if (students.isSuccess()) {
                output.info("Estudiantes de " + link.getGuardian().getFullName() + " (" + link.getGuardian().getRelationship() + "):");
                printer.students(students.getData());
            } else {
                output.error(students.getMessage());
            }
        }
    }

    /** Lets the user pick one of the student's guardians; null if the list could not be loaded. */
    private StudentGuardian chooseLink(Student student, String prompt) {
        Result<List<StudentGuardian>> result = controller.guardiansOf(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return null;
        }
        return input.readOption(prompt, result.getData(),
                l -> l.getGuardian().getFullName() + " (" + l.getGuardian().getRelationship()
                        + (l.isPrincipal() ? ", principal" : "") + ")");
    }
}
