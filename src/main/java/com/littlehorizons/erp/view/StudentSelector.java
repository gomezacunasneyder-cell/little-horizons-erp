package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.model.entity.Student;

import java.util.List;

/** Reusable dialog: the user types part of a name or a document and picks one student. */
final class StudentSelector {

    private static final int MAX_RESULTS = 15;

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final StudentController controller;

    StudentSelector(ConsoleInput input, ConsoleOutput output, StudentController controller) {
        this.input = input;
        this.output = output;
        this.controller = controller;
    }

    /** Asks until one student is chosen. The user can type "cancelar" to leave. */
    Student select() {
        while (true) {
            String term = input.readRequiredText("Documento, nombre o apellido del estudiante: ", 100);
            Result<List<Student>> result = controller.search(term);
            if (!result.isSuccess()) {
                output.error(result.getMessage());
                continue;
            }
            List<Student> found = result.getData();
            if (found.isEmpty()) {
                output.error("No se encontraron estudiantes con «" + term + "». Intente de nuevo o escriba cancelar.");
            } else if (found.size() > MAX_RESULTS) {
                output.error("Hay " + found.size() + " coincidencias. Escriba más datos para acotar la búsqueda.");
            } else if (found.size() == 1) {
                return found.get(0);
            } else {
                return input.readOption("Seleccione el estudiante: ", found,
                        s -> s.getFullName() + " - " + s.getDocumentType().getDbLabel() + " " + s.getDocumentNumber());
            }
        }
    }
}
