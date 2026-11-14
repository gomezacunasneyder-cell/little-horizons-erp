package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.TeacherStatus;

import java.util.List;

/** Module 2: teacher directory. Assignments and schedules have their own menu. */
public final class TeacherMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar profesor",
            "Listar profesores",
            "Buscar profesor",
            "Ver ficha del profesor",
            "Editar profesor",
            "Cambiar estado (Activo, Inactivo, Licencia)",
            "Asignaciones, horarios y carga académica");

    private final TeacherController controller;
    private final EntitySelector<Teacher> selector;
    private final TeacherForm form;
    private final ResultPrinter printer;
    private final MenuView assignmentMenu;

    public TeacherMenuView(ConsoleInput input, ConsoleOutput output, TeacherController controller, MenuView assignmentMenu) {
        super(input, output);
        this.controller = controller;
        this.selector = teacherSelector(input, output, controller);
        this.form = new TeacherForm(input, controller);
        this.printer = new ResultPrinter(output);
        this.assignmentMenu = assignmentMenu;
    }

    /** Builds the dialog that finds and picks one teacher; shared with the assignment menu. */
    static EntitySelector<Teacher> teacherSelector(ConsoleInput input, ConsoleOutput output, TeacherController controller) {
        return new EntitySelector<>(input, output, "Documento, nombre, apellido o especialización del profesor: ",
                controller::search,
                t -> t.getFullName() + " - " + t.getSpecialization() + " (" + t.getDocumentNumber() + ")");
    }

    @Override
    protected String title() {
        return "GESTIÓN DE PROFESORES";
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
            case 6 -> changeStatus();
            case 7 -> assignmentMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void register() {
        output.title("Registrar profesor (escriba «cancelar» para salir)");
        printer.report(controller.register(form.readNewTeacher()));
    }

    private void list() {
        print(controller.listAll());
    }

    private void search() {
        print(controller.search(input.readRequiredText("Documento, nombre, apellido o especialización: ", 100)));
    }

    private void print(Result<List<Teacher>> result) {
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.table(List.of("Código", "Documento", "Nombre", "Especialización", "Estado"),
                result.getData().stream()
                        .map(t -> List.of(String.valueOf(t.getId()), t.getDocumentType().getDbLabel() + " " + t.getDocumentNumber(),
                                t.getFullName(), t.getSpecialization(), t.getStatus().getDisplayName()))
                        .toList());
    }

    private void showProfile() {
        Teacher teacher = selector.select();
        output.title("Ficha de " + teacher.getFullName());
        output.line("Código:          " + teacher.getId());
        output.line("Documento:       " + teacher.getDocumentType().getDbLabel() + " " + teacher.getDocumentNumber());
        output.line("Correo:          " + teacher.getEmail());
        output.line("Teléfono:        " + (teacher.getPhone() == null ? "-" : teacher.getPhone()));
        output.line("Especialización: " + teacher.getSpecialization());
        output.line("Contratación:    " + teacher.getHireDate());
        output.line("Estado:          " + teacher.getStatus().getDisplayName());
    }

    private void edit() {
        Teacher teacher = selector.select();
        output.info("Editando a " + teacher.getFullName() + ". Enter conserva el valor actual.");
        form.edit(teacher);
        printer.report(controller.update(teacher));
    }

    private void changeStatus() {
        Teacher teacher = selector.select();
        output.info("Estado actual: " + teacher.getStatus().getDisplayName());
        TeacherStatus newStatus = input.readOption("Nuevo estado: ", List.of(TeacherStatus.values()), TeacherStatus::getDisplayName);
        printer.report(controller.changeStatus(teacher, newStatus));
    }
}
