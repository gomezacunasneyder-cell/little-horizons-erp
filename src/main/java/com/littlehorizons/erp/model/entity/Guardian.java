package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.DocumentType;

import java.util.List;

/**
 * Parent or legal guardian of one or more students. Like Student it extends Person; it adds
 * what only a guardian has: relationship with the student, a main phone (mandatory, it is the
 * contact channel), an optional second phone, occupation and whether this person is
 * financially responsible (the one who pays and appears on invoices).
 */
public class Guardian extends Person {

    private static final int RELATIONSHIP_MAX = 50;

    /** The only relationships the school accepts (the console forms offer exactly this list). */
    public static final List<String> RELATIONSHIPS = List.of("Madre", "Padre", "Abuelo", "Tío", "Tutor legal");
    public static final String RELATIONSHIP_MESSAGE = "Parentesco inválido. Opciones: " + String.join(", ", RELATIONSHIPS) + ".";
    private static final int OCCUPATION_MAX = 100;

    private String relationship;
    private String secondaryPhone;
    private String occupation;
    private boolean financiallyResponsible = true;

    public Guardian(DocumentType documentType, String documentNumber, String firstName,
                    String lastName, String relationship, String phone) {
        super(documentType, documentNumber, firstName, lastName);
        if (documentType == DocumentType.TI) {
            throw new ValidationException("Un acudiente no puede tener tarjeta de identidad (use CC, CE o Pasaporte).");
        }
        setRelationship(relationship);
        setPhone(phone);
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        String typed = requireLabel(relationship, RELATIONSHIP_MAX, "El parentesco");
        this.relationship = RELATIONSHIPS.stream()
                .filter(option -> option.equalsIgnoreCase(typed))
                .findFirst()
                .orElseThrow(() -> new ValidationException(RELATIONSHIP_MESSAGE));
    }

    /** The main phone is mandatory for a guardian. */
    @Override
    public void setPhone(String phone) {
        super.setPhone(phone);
        if (getPhone() == null) {
            throw new ValidationException("El teléfono principal es obligatorio.");
        }
    }

    public String getSecondaryPhone() {
        return secondaryPhone;
    }

    public void setSecondaryPhone(String secondaryPhone) {
        this.secondaryPhone = optionalMatching(secondaryPhone, PHONE_PATTERN, PHONE_MESSAGE);
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = optionalLabel(occupation, OCCUPATION_MAX, "Ocupación");
    }

    public boolean isFinanciallyResponsible() {
        return financiallyResponsible;
    }

    public void setFinanciallyResponsible(boolean financiallyResponsible) {
        this.financiallyResponsible = financiallyResponsible;
    }
}
