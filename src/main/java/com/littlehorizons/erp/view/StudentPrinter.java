package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;

import java.util.List;

/** Formats students and guardians as console tables; shared by several views (DRY). */
final class StudentPrinter {

    private final ConsoleOutput output;

    StudentPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void students(List<Student> students) {
        output.table(List.of("Código", "Documento", "Nombre", "Estado"),
                students.stream()
                        .map(s -> List.of(String.valueOf(s.getId()),
                                s.getDocumentType().getDbLabel() + " " + s.getDocumentNumber(),
                                s.getFullName(), s.getStatus().getDisplayName()))
                        .toList());
    }

    void guardians(List<StudentGuardian> links) {
        output.table(List.of("Documento", "Nombre", "Parentesco", "Teléfono", "Principal", "Emergencia"),
                links.stream()
                        .map(l -> List.of(l.getGuardian().getDocumentNumber(), l.getGuardian().getFullName(),
                                l.getGuardian().getRelationship(), l.getGuardian().getPhone(),
                                l.isPrincipal() ? "Sí" : "No", l.isEmergencyContact() ? "Sí" : "No"))
                        .toList());
    }

    /** Prints the message of a Result as success or error. */
    void report(Result<?> result) {
        if (result.isSuccess()) {
            output.success(result.getMessage());
        } else {
            output.error(result.getMessage());
        }
    }
}
