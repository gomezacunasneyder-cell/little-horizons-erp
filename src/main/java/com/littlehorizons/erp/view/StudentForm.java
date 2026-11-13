package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.StudentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/** Collects and edits student data, asking each question until the answer is valid. */
final class StudentForm {

    private static final Pattern BLOOD_TYPE = Pattern.compile(Student.BLOOD_TYPE_PATTERN.pattern(), Pattern.CASE_INSENSITIVE);
    private static final String BLOOD_TYPE_MESSAGE = "Tipo de sangre inválido. Ejemplos: O+, A-, AB+.";

    private final ConsoleInput input;
    private final StudentController controller;

    StudentForm(ConsoleInput input, StudentController controller) {
        this.input = input;
        this.controller = controller;
    }

    Student readNewStudent() {
        DocumentType type = input.readOption("Tipo de documento: ", List.of(DocumentType.values()), DocumentType::getDisplayName);
        String document = input.read("Número de documento: ", text -> {
            Person.checkDocumentNumber(type, text);
            if (controller.findByDocument(text.trim()).isSuccess()) {
                throw new ValidationException("Ya existe un estudiante con ese documento.");
            }
            return text;
        });
        String firstName = input.readMatching("Primer nombre: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String middleName = input.readOptionalMatching("Segundo nombre (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String lastName = input.readMatching("Primer apellido: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String secondLastName = input.readOptionalMatching("Segundo apellido (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        Gender gender = input.readOption("Género: ", List.of(Gender.values()), Gender::getDisplayName);
        LocalDate today = LocalDate.now();
        LocalDate birthDate = input.readDate("Fecha de nacimiento (aaaa-mm-dd): ",
                date -> date.isBefore(today) && date.isAfter(today.minusYears(30)),
                "La fecha debe estar en el pasado y ser razonable para un estudiante.");
        LocalDate admissionDate = input.read("Fecha de ingreso (aaaa-mm-dd, Enter = hoy): ", text -> {
            LocalDate date = text.isEmpty() ? today : LocalDate.parse(text);
            if (date.isAfter(today) || date.isBefore(birthDate)) {
                throw new ValidationException("La fecha de ingreso no puede ser futura ni anterior al nacimiento.");
            }
            return date;
        });
        return Student.builder()
                .documentType(type).documentNumber(document)
                .firstName(firstName).middleName(middleName)
                .lastName(lastName).secondLastName(secondLastName)
                .gender(gender).birthDate(birthDate).admissionDate(admissionDate)
                .address(input.readOptionalText("Dirección (Enter para omitir): ", 255))
                .phone(input.readOptionalMatching("Teléfono (Enter para omitir): ", Person.PHONE_PATTERN, Person.PHONE_MESSAGE))
                .email(input.readOptionalMatching("Correo (Enter para omitir): ", Person.EMAIL_PATTERN, Person.EMAIL_MESSAGE))
                .bloodType(input.readOptionalMatching("Tipo de sangre, ej. O+ (Enter para omitir): ", BLOOD_TYPE, BLOOD_TYPE_MESSAGE))
                .allergies(input.readOptionalText("Alergias (Enter para omitir): ", 2000))
                .medicalConditions(input.readOptionalText("Condiciones médicas (Enter para omitir): ", 2000))
                .build();
    }

    /** Edits the student in place. Enter keeps the current value; "-" clears an optional one. */
    void edit(Student student) {
        student.setFirstName(editMatching("Primer nombre", student.getFirstName(), true, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        student.setMiddleName(editMatching("Segundo nombre", student.getMiddleName(), false, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        student.setLastName(editMatching("Primer apellido", student.getLastName(), true, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        student.setSecondLastName(editMatching("Segundo apellido", student.getSecondLastName(), false, Person.NAME_PATTERN, Person.NAME_MESSAGE));
        student.setPhone(editMatching("Teléfono", student.getPhone(), false, Person.PHONE_PATTERN, Person.PHONE_MESSAGE));
        student.setEmail(editMatching("Correo", student.getEmail(), false, Person.EMAIL_PATTERN, Person.EMAIL_MESSAGE));
        student.setAddress(editMatching("Dirección", student.getAddress(), false, Pattern.compile(".{1,255}"), "Máximo 255 caracteres."));
        student.setBloodType(editMatching("Tipo de sangre", student.getBloodType(), false, BLOOD_TYPE, BLOOD_TYPE_MESSAGE));
        student.setAllergies(editMatching("Alergias", student.getAllergies(), false, Pattern.compile(".{1,2000}"), "Máximo 2000 caracteres."));
        student.setMedicalConditions(editMatching("Condiciones médicas", student.getMedicalConditions(), false, Pattern.compile(".{1,2000}"), "Máximo 2000 caracteres."));
        if (input.readYesNo("¿Cambiar el estado (actual: " + student.getStatus().getDisplayName() + ")?")) {
            student.setStatus(input.readOption("Nuevo estado: ",
                    List.of(StudentStatus.ACTIVE, StudentStatus.SUSPENDED, StudentStatus.GRADUATED),
                    StudentStatus::getDisplayName));
        }
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
