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

    private static final Pattern NUMERIC_DOCUMENT_PATTERN = Pattern.compile("[0-9]{5,15}");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("[A-Za-z0-9]{5,20}");
    public static final Pattern NAME_PATTERN = Pattern.compile("\\p{L}+(?:[ '\\-]\\p{L}+)*");
    /** Free descriptive text that must be words (specialization, occupation, relationship). */
    public static final Pattern LABEL_PATTERN = Pattern.compile("\\p{L}[\\p{L} '.,/\\-]*");
    public static final String LABEL_MESSAGE = "Use solo letras (se permiten espacios, punto, coma, guion y barra).";
    public static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{7,15}");
    public static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)+");

    private static final String NUMERIC_DOCUMENT_MESSAGE = "El documento debe tener solo números (entre 5 y 15 dígitos), sin puntos ni espacios.";
    private static final String PASSPORT_MESSAGE = "El pasaporte debe tener entre 5 y 20 letras o números, sin espacios.";
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

    /**
     * Validates a document number for the given type: TI, CC and CE are digits only, passports
     * may also contain letters. Returns the trimmed number or throws {@link ValidationException}.
     * Console forms call this so they reuse the exact rule of the entity.
     */
    public static String checkDocumentNumber(DocumentType type, String number) {
        if (type == DocumentType.PASSPORT) {
            return requireMatching(number, PASSPORT_PATTERN, PASSPORT_MESSAGE);
        }
        return requireMatching(number, NUMERIC_DOCUMENT_PATTERN, NUMERIC_DOCUMENT_MESSAGE);
    }

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

    /** Text made of letters only (see LABEL_PATTERN) with a maximum length; blank returns null. */
    public static String optionalLabel(String value, int maxLength, String fieldName) {
        String text = optionalText(value, maxLength, fieldName);
        if (text != null && !LABEL_PATTERN.matcher(text).matches()) {
            throw new ValidationException(fieldName + ": " + LABEL_MESSAGE);
        }
        return text;
    }

    public static String requireLabel(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " es obligatorio.");
        }
        return optionalLabel(value, maxLength, fieldName);
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
        if (documentNumber != null) {
            checkDocumentNumber(documentType, documentNumber);
        }
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = checkDocumentNumber(documentType, documentNumber);
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
