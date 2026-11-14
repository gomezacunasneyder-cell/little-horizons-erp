package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.model.entity.Student;

/** Dialog to find and pick one student; the generic work is done by EntitySelector. */
final class StudentSelector {

    private final EntitySelector<Student> selector;

    StudentSelector(ConsoleInput input, ConsoleOutput output, StudentController controller) {
        this.selector = new EntitySelector<>(input, output, "Documento, nombre o apellido del estudiante: ",
                controller::search,
                s -> s.getFullName() + " - " + s.getDocumentType().getDbLabel() + " " + s.getDocumentNumber());
    }

    /** Asks until one student is chosen. The user can type "cancelar" to leave. */
    Student select() {
        return selector.select();
    }
}
