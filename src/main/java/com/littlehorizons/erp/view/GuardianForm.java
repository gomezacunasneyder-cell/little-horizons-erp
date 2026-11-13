package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.DocumentType;

import java.util.List;
import java.util.Optional;

/**
 * Collects the data of a guardian. If the document already belongs to a registered guardian
 * (for example the parent of a brother or sister), that guardian is reused instead of typed
 * again, so one guardian can be linked to several students.
 */
final class GuardianForm {

    private static final List<DocumentType> GUARDIAN_DOCUMENTS =
            List.of(DocumentType.CC, DocumentType.CE, DocumentType.PASSPORT);

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final StudentController controller;

    GuardianForm(ConsoleInput input, ConsoleOutput output, StudentController controller) {
        this.input = input;
        this.output = output;
        this.controller = controller;
    }

    /**
     * Reads one guardian link. {@code alreadyChosen} holds the document numbers already used in
     * this operation, to avoid adding the same person twice.
     */
    StudentGuardian readLink(boolean mustBePrincipal, List<String> alreadyChosen) {
        while (true) {
            DocumentType type = input.readOption("Tipo de documento del acudiente: ", GUARDIAN_DOCUMENTS, DocumentType::getDisplayName);
            String document = input.read("Documento del acudiente: ", text -> {
                Person.checkDocumentNumber(type, text);
                if (alreadyChosen.contains(text)) {
                    throw new ValidationException("Ese acudiente ya fue agregado.");
                }
                return text;
            });
            Result<Optional<Guardian>> found = controller.findGuardian(document);
            if (!found.isSuccess()) {
                output.error(found.getMessage());
                continue;
            }
            Guardian guardian;
            if (found.getData().isPresent()) {
                guardian = found.getData().get();
                output.info("Acudiente ya registrado: " + guardian.getFullName() + " (" + guardian.getRelationship() + ").");
                if (!input.readYesNo("¿Asociarlo a este estudiante?")) {
                    continue;
                }
            } else {
                output.info("Acudiente nuevo. Complete sus datos.");
                guardian = readNewGuardian(type, document);
            }
            boolean principal = mustBePrincipal || input.readYesNo("¿Es el acudiente principal?");
            boolean emergency = input.readYesNo("¿Es contacto de emergencia?");
            alreadyChosen.add(document);
            return new StudentGuardian(guardian, principal, emergency);
        }
    }

    private Guardian readNewGuardian(DocumentType type, String document) {
        String firstName = input.readMatching("Primer nombre: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String middleName = input.readOptionalMatching("Segundo nombre (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String lastName = input.readMatching("Primer apellido: ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String secondLastName = input.readOptionalMatching("Segundo apellido (Enter para omitir): ", Person.NAME_PATTERN, Person.NAME_MESSAGE);
        String relationship = input.readRequiredText("Parentesco (Padre, Madre, Abuelo, Tío, Tutor legal...): ", 50);
        String phone = input.readMatching("Teléfono principal: ", Person.PHONE_PATTERN, Person.PHONE_MESSAGE);

        Guardian guardian = new Guardian(type, document, firstName, lastName, relationship, phone);
        guardian.setMiddleName(middleName);
        guardian.setSecondLastName(secondLastName);
        guardian.setSecondaryPhone(input.readOptionalMatching("Teléfono secundario (Enter para omitir): ", Person.PHONE_PATTERN, Person.PHONE_MESSAGE));
        guardian.setEmail(input.readOptionalMatching("Correo (Enter para omitir): ", Person.EMAIL_PATTERN, Person.EMAIL_MESSAGE));
        guardian.setAddress(input.readOptionalText("Dirección (Enter para omitir): ", 255));
        guardian.setOccupation(input.readOptionalText("Ocupación (Enter para omitir): ", 100));
        guardian.setFinanciallyResponsible(input.readYesNo("¿Es responsable de los pagos?"));
        return guardian;
    }

    /** Edits the editable data of an existing guardian; Enter keeps the current value. */
    void edit(Guardian guardian) {
        guardian.setRelationship(editText("Parentesco", guardian.getRelationship(), true, 50));
        guardian.setPhone(editMatching("Teléfono principal", guardian.getPhone(), true, Person.PHONE_PATTERN, Person.PHONE_MESSAGE));
        guardian.setSecondaryPhone(editMatching("Teléfono secundario", guardian.getSecondaryPhone(), false, Person.PHONE_PATTERN, Person.PHONE_MESSAGE));
        guardian.setEmail(editMatching("Correo", guardian.getEmail(), false, Person.EMAIL_PATTERN, Person.EMAIL_MESSAGE));
        guardian.setAddress(editText("Dirección", guardian.getAddress(), false, 255));
        guardian.setOccupation(editText("Ocupación", guardian.getOccupation(), false, 100));
    }

    private String editText(String label, String current, boolean required, int maxLength) {
        return input.read(editPrompt(label, current, required), text -> {
            if (text.isEmpty()) {
                return current;
            }
            if (text.equals("-") && !required) {
                return null;
            }
            if (text.length() > maxLength) {
                throw new ValidationException("Máximo " + maxLength + " caracteres.");
            }
            return text;
        });
    }

    private String editMatching(String label, String current, boolean required,
                                java.util.regex.Pattern pattern, String message) {
        return input.read(editPrompt(label, current, required), text -> {
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

    private String editPrompt(String label, String current, boolean required) {
        return label + " [" + (current == null ? "vacío" : current) + "] (Enter = mantener"
                + (required ? "" : ", - = borrar") + "): ";
    }
}
