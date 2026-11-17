package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.model.entity.Borrower;
import com.littlehorizons.erp.model.enums.BorrowerType;

import java.util.List;

/**
 * Dialog to pick who asks for a resource: first the kind (student or teacher), then the person.
 * The person is found with the same EntitySelector the other modules use; the search of each
 * controller is adapted to Borrower with Result.map, so the loan menu never touches Student or Teacher.
 */
final class BorrowerSelector {

    private final ConsoleInput input;
    private final EntitySelector<Borrower> studentSelector;
    private final EntitySelector<Borrower> teacherSelector;

    BorrowerSelector(ConsoleInput input, ConsoleOutput output, StudentController students, TeacherController teachers) {
        this.input = input;
        this.studentSelector = new EntitySelector<>(input, output, "Documento, nombre o apellido del estudiante: ",
                term -> students.search(term).map(found -> found.stream().map(Borrower::of).toList()), Borrower::label);
        this.teacherSelector = new EntitySelector<>(input, output,
                "Documento, nombre, apellido o especialización del profesor: ",
                term -> teachers.search(term).map(found -> found.stream().map(Borrower::of).toList()), Borrower::label);
    }

    Borrower select() {
        BorrowerType type = input.readOption("Tipo de solicitante (número): ", List.of(BorrowerType.values()),
                BorrowerType::getDisplayName);
        return (type == BorrowerType.STUDENT ? studentSelector : teacherSelector).select();
    }
}
