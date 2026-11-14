package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.DocumentType;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/** Collects and edits teacher data, asking each question until the answer is valid. */
final class TeacherForm {

    private static final List<DocumentType> TEACHER_DOCUMENTS =
            List.of(DocumentType.CC, DocumentType.CE, DocumentType.PASSPORT);

    private final ConsoleInput input;
    private final TeacherController controller;

    TeacherForm(ConsoleInput input, TeacherController controller) {
        this.input = input;
        this.controller = controller;
    }

    Teacher readNewTeacher() {
        DocumentType type = input.readOption("Tipo de documento: ", TEACHER_DOCUMENTS, DocumentType::getDisplayName);
        String document = input.read("Número de documento: ", text -> {
            Person.checkDocumentNumber(type, text);
            if (controller.findByDocument(text.trim()).isSuccess()) {
                throw new ValidationException("Ya existe un profesor con ese documento.");
            }
            return text;
        });
        String firstName = input.readMatching("Primer nombre: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String middleName = input.readOptionalMatching("Segundo nombre (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String lastName = input.readMatching("Primer apellido: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String secondLastName = input.readOptionalMatching("Segundo apellido (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String email = input.read("Correo electrónico: ", text -> {
            if (!Person.EMAIL_PATTERN.matcher(text).matches()) {
                throw new ValidationException(Person.EMAIL_MESSAGE);
            }
            Result<Boolean> taken = controller.emailTaken(text);
            if (taken.isSuccess() && taken.getData()) {
                throw new ValidationException("Ese correo ya pertenece a otro profesor.");
            }
            return text;
        });
        String phone = input.readOptionalMatching("Teléfono (Enter para omitir): ", Person.PHONE_PATTERN, Person.PHONE_MESSAGE);
        String specialization = input.read("Especialización (ejemplo: Matemáticas): ", text -> Person.requireLabel(text, 100, "La especialización"));
        LocalDate hireDate = input.readDate("Fecha de contratación (aaaa-mm-dd): ",
                date -> !date.isAfter(LocalDate.now()) && date.getYear() >= 1950,
                "La fecha no puede estar en el futuro ni ser anterior a 1950.");

        Teacher teacher = new Teacher(type, document, firstName, lastName, email, specialization, hireDate);
        teacher.setMiddleName(middleName);
        teacher.setSecondLastName(secondLastName);
        teacher.setPhone(phone);
        return teacher;
    }

    /** Edits the teacher in place. Enter keeps the current value; "-" clears an optional one. */
    void edit(Teacher teacher) {
        teacher.setFirstName(editMatching("Primer nombre", teacher.getFirstName(), true, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        teacher.setMiddleName(editMatching("Segundo nombre", teacher.getMiddleName(), false, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        teacher.setLastName(editMatching("Primer apellido", teacher.getLastName(), true, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        teacher.setSecondLastName(editMatching("Segundo apellido", teacher.getSecondLastName(), false, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        teacher.setEmail(editMatching("Correo", teacher.getEmail(), true, Person.EMAIL_PATTERN, Person.EMAIL_MESSAGE));
        teacher.setPhone(editMatching("Teléfono", teacher.getPhone(), false, Person.PHONE_PATTERN, Person.PHONE_MESSAGE));
        teacher.setSpecialization(editMatching("Especialización", teacher.getSpecialization(), true, Person.LABEL_PATTERN, Person.LABEL_MESSAGE));
    }

    private String editMatching(String label, String current, boolean required, Pattern pattern, String message) {
        String prompt = label + " [" + (current == null ? "vacío" : current) + "] (Enter = mantener"
                + (required ? "" : ", - = borrar") + "): ";
        return input.read(prompt, text -> {
            if (text.isEmpty()) {
                return current;
            }
            if (text.equals("-") && !required) {
                return null;
            }
            if (!pattern.matcher(text).matches()) {
                throw new ValidationException(message);
            }
            return text;
        });
    }
}
