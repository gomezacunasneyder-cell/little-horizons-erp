package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;

import java.util.ArrayList;
import java.util.List;

/** Module 1: students and their guardians. Enrollment and subjects have their own menus. */
public final class StudentMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar estudiante",
            "Listar estudiantes",
            "Buscar estudiante",
            "Ver ficha del estudiante",
            "Editar estudiante",
            "Retirar estudiante",
            "Gestionar acudientes",
            "Matrículas",
            "Inscripción en materias");

    private final StudentController controller;
    private final StudentSelector selector;
    private final StudentPrinter printer;
    private final StudentForm studentForm;
    private final GuardianForm guardianForm;
    private final MenuView guardianMenu;
    private final MenuView enrollmentMenu;
    private final MenuView registrationMenu;

    public StudentMenuView(ConsoleInput input, ConsoleOutput output, StudentController controller,
                           MenuView guardianMenu, MenuView enrollmentMenu, MenuView registrationMenu) {
        super(input, output);
        this.controller = controller;
        this.selector = new StudentSelector(input, output, controller);
        this.printer = new StudentPrinter(output);
        this.studentForm = new StudentForm(input, controller);
        this.guardianForm = new GuardianForm(input, output, controller);
        this.guardianMenu = guardianMenu;
        this.enrollmentMenu = enrollmentMenu;
        this.registrationMenu = registrationMenu;
    }

    @Override
    protected String title() {
        return "GESTIÓN DE ESTUDIANTES";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> register();
            case 2 -> list();
            case 3 -> search();
            case 4 -> showProfile();
            case 5 -> edit();
            case 6 -> retire();
            case 7 -> guardianMenu.show();
            case 8 -> enrollmentMenu.show();
            case 9 -> registrationMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void register() {
        output.title("Registrar estudiante (escriba «cancelar» para salir)");
        Student student = studentForm.readNewStudent();

        output.title("Acudientes (mínimo uno)");
        List<StudentGuardian> guardians = new ArrayList<>();
        List<String> chosen = new ArrayList<>();
        do {
            guardians.add(guardianForm.readLink(guardians.isEmpty(), chosen));
        } while (input.readYesNo("¿Agregar otro acudiente?"));

        Result<Student> result = controller.register(student, guardians);
        printer.report(result);
    }

    private void list() {
        Result<List<Student>> result = controller.listAll();
        if (result.isSuccess()) {
            printer.students(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void search() {
        String term = input.readRequiredText("Documento, nombre o apellido: ", 100);
        Result<List<Student>> result = controller.search(term);
        if (result.isSuccess()) {
            printer.students(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void showProfile() {
        Student student = selector.select();
        output.title("Ficha de " + student.getFullName());
        output.line("Código:            " + student.getId());
        output.line("Documento:         " + student.getDocumentType().getDbLabel() + " " + student.getDocumentNumber());
        output.line("Nacimiento:        " + student.getBirthDate() + " (" + student.getGender().getDisplayName() + ")");
        output.line("Estado:            " + student.getStatus().getDisplayName());
        output.line("Ingreso:           " + student.getAdmissionDate());
        output.line("Teléfono / correo: " + orDash(student.getPhone()) + " / " + orDash(student.getEmail()));
        output.line("Dirección:         " + orDash(student.getAddress()));
        output.line("Tipo de sangre:    " + orDash(student.getBloodType()));
        output.line("Alergias:          " + orDash(student.getAllergies()));
        output.line("Cond. médicas:     " + orDash(student.getMedicalConditions()));

        Result<List<StudentGuardian>> guardians = controller.guardiansOf(student);
        if (guardians.isSuccess()) {
            output.title("Acudientes y contactos de emergencia");
            printer.guardians(guardians.getData());
        } else {
            output.error(guardians.getMessage());
        }
    }

    private void edit() {
        Student student = selector.select();
        output.info("Editando a " + student.getFullName() + ". Enter conserva el valor actual.");
        studentForm.edit(student);
        printer.report(controller.update(student));
    }

    private void retire() {
        Student student = selector.select();
        output.info("Retirar cierra sus matrículas e inscripciones activas. El historial se conserva.");
        if (input.readYesNo("¿Retirar a " + student.getFullName() + "?")) {
            printer.report(controller.retire(student));
        } else {
            output.info("No se realizaron cambios.");
        }
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }
}
