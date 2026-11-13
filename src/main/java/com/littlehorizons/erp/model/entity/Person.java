package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.DocumentType;

import java.util.regex.Pattern;

/**
 * Common data of every person the school registers (student, guardian, later teacher).
 * Inheritance is used because a Student IS-A Person, and the identity data and its validation
 * rules are exactly the same for all of them. The class is abstract: a "generic person" never
 * exists on its own.
 *
 * Encapsulation: every field is private and every setter validates before assigning, so an
 * invalid Person cannot exist in memory. The validation patterns are public constants so the
 * console forms can reuse the very same rules (no duplicated regular expressions).
 */
public abstract class Person {

    public static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile("[A-Za-z0-9]{5,20}");
    public static final Pattern NAME_PATTERN = Pattern.compile("\\p{L}+(?:[ '\\-]\\p{L}+)*");
    public static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{7,15}");
    public static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)+");

    public static final String DOCUMENT_NUMBER_MESSAGE = "El documento debe tener entre 5 y 20 letras o números, sin espacios.";
    public static final String NAME_MESSAGE = "Use solo letras (se permiten espacios, guion y apóstrofe).";
    public static final String PHONE_MESSAGE = "El teléfono debe tener entre 7 y 15 dígitos (puede iniciar con +).";
    public static final String EMAIL_MESSAGE = "Correo inválido. Ejemplo: nombre@dominio.com";

    private static final int NAME_MAX = 100;
    private static final int EMAIL_MAX = 100;
    private static final int ADDRESS_MAX = 255;

    private Integer id;
    private DocumentType documentType;
    private String documentNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    private String secondLastName;
    private String email;
    private String address;
    private String phone;

    protected Person(DocumentType documentType, String documentNumber, String firstName, String lastName) {
        setDocumentType(documentType);
        setDocumentNumber(documentNumber);
        setFirstName(firstName);
        setLastName(lastName);
    }

    // ---- validation helpers shared by Person and its subclasses ----

    protected static String requireMatching(String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value.trim()).matches()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    /** Blank becomes null (optional field); otherwise it must match the pattern. */
    protected static String optionalMatching(String value, Pattern pattern, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireMatching(value, pattern, message);
    }

    protected static String optionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > maxLength) {
            throw new ValidationException(fieldName + ": máximo " + maxLength + " caracteres.");
        }
        return value.trim();
    }

    protected static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " es obligatorio.");
        }
        return optionalText(value, maxLength, fieldName);
    }

    // ---- getters and validated setters ----

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        if (documentType == null) {
            throw new ValidationException("El tipo de documento es obligatorio.");
        }
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = requireMatching(documentNumber, DOCUMENT_NUMBER_PATTERN, DOCUMENT_NUMBER_MESSAGE);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = requireMatching(firstName, NAME_PATTERN, "Primer nombre: " + NAME_MESSAGE);
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = optionalMatching(middleName, NAME_PATTERN, "Segundo nombre: " + NAME_MESSAGE);
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = requireMatching(lastName, NAME_PATTERN, "Primer apellido: " + NAME_MESSAGE);
    }

    public String getSecondLastName() {
        return secondLastName;
    }

    public void setSecondLastName(String secondLastName) {
        this.secondLastName = optionalMatching(secondLastName, NAME_PATTERN, "Segundo apellido: " + NAME_MESSAGE);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = optionalMatching(email, EMAIL_PATTERN, EMAIL_MESSAGE);
        if (this.email != null && this.email.length() > EMAIL_MAX) {
            throw new ValidationException("Correo: máximo " + EMAIL_MAX + " caracteres.");
        }
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = optionalText(address, ADDRESS_MAX, "Dirección");
    }

    public String getPhone() {
        return phone;
    }

    /** Subclasses decide if the phone is mandatory; here the format is checked when present. */
    public void setPhone(String phone) {
        this.phone = optionalMatching(phone, PHONE_PATTERN, PHONE_MESSAGE);
    }

    /** "First Middle Last SecondLast", skipping the names that were not given. */
    public String getFullName() {
        StringBuilder name = new StringBuilder(firstName);
        if (middleName != null) {
            name.append(' ').append(middleName);
        }
        name.append(' ').append(lastName);
        if (secondLastName != null) {
            name.append(' ').append(secondLastName);
        }
        return name.toString();
    }

    @Override
    public String toString() {
        return getFullName() + " (" + documentType.getDbLabel() + " " + documentNumber + ")";
    }
}
