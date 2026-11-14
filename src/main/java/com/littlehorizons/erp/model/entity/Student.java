package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.StudentStatus;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * A student of the school. A Student has many optional data (second name, phone, blood type,
 * allergies...), so it is created with the BUILDER pattern: instead of a constructor with
 * twelve positional arguments (easy to mix up), the caller names each value:
 *
 *   Student student = Student.builder()
 *           .documentType(DocumentType.TI).documentNumber("1002003004")
 *           .firstName("Ana").lastName("Gómez")
 *           .gender(Gender.FEMALE).birthDate(LocalDate.of(2015, 3, 2))
 *           .admissionDate(LocalDate.now()).build();
 *
 * build() runs every validation, so a Student that exists is always valid.
 */
public class Student extends Person {

    public static final Pattern BLOOD_TYPE_PATTERN = Pattern.compile("(A|B|AB|O)[+-]");
    private static final int MAX_AGE_YEARS = 30;
    private static final int TEXT_MAX = 2000;

    private Gender gender;
    private LocalDate birthDate;
    private String bloodType;
    private String allergies;
    private String medicalConditions;
    private LocalDate admissionDate;
    private StudentStatus status = StudentStatus.ACTIVE;

    private Student(Builder b) {
        super(b.documentType, b.documentNumber, b.firstName, b.lastName);
        setMiddleName(b.middleName);
        setSecondLastName(b.secondLastName);
        setGender(b.gender);
        setBirthDate(b.birthDate);
        setAddress(b.address);
        setPhone(b.phone);
        setEmail(b.email);
        setBloodType(b.bloodType);
        setAllergies(b.allergies);
        setMedicalConditions(b.medicalConditions);
        setAdmissionDate(b.admissionDate);
        setStatus(b.status);
        setId(b.id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        if (gender == null) {
            throw new ValidationException("El género es obligatorio.");
        }
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ValidationException("La fecha de nacimiento es obligatoria.");
        }
        if (!birthDate.isBefore(LocalDate.now())) {
            throw new ValidationException("La fecha de nacimiento debe estar en el pasado.");
        }
        if (birthDate.isBefore(LocalDate.now().minusYears(MAX_AGE_YEARS))) {
            throw new ValidationException("La fecha de nacimiento no es válida para un estudiante.");
        }
        this.birthDate = birthDate;
    }

    public String getBloodType() {
        return bloodType;
    }

    public void setBloodType(String bloodType) {
        this.bloodType = optionalMatching(bloodType == null ? null : bloodType.toUpperCase(),
                BLOOD_TYPE_PATTERN, "Tipo de sangre inválido. Ejemplos: O+, A-, AB+.");
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = optionalLabel(allergies, TEXT_MAX, "Alergias");
    }

    public String getMedicalConditions() {
        return medicalConditions;
    }

    public void setMedicalConditions(String medicalConditions) {
        this.medicalConditions = optionalLabel(medicalConditions, TEXT_MAX, "Condiciones médicas");
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) {
        if (admissionDate == null) {
            throw new ValidationException("La fecha de ingreso es obligatoria.");
        }
        if (admissionDate.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de ingreso no puede estar en el futuro.");
        }
        if (birthDate != null && admissionDate.isBefore(birthDate)) {
            throw new ValidationException("La fecha de ingreso no puede ser anterior al nacimiento.");
        }
        this.admissionDate = admissionDate;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public void setStatus(StudentStatus status) {
        this.status = status == null ? StudentStatus.ACTIVE : status;
    }

    /** Builder: collects the values and creates a validated Student in build(). */
    public static final class Builder {
        private Integer id;
        private DocumentType documentType;
        private String documentNumber;
        private String firstName;
        private String middleName;
        private String lastName;
        private String secondLastName;
        private Gender gender;
        private LocalDate birthDate;
        private String address;
        private String phone;
        private String email;
        private String bloodType;
        private String allergies;
        private String medicalConditions;
        private LocalDate admissionDate;
        private StudentStatus status;

        private Builder() {
        }

        public Builder id(Integer id) { this.id = id; return this; }
        public Builder documentType(DocumentType value) { this.documentType = value; return this; }
        public Builder documentNumber(String value) { this.documentNumber = value; return this; }
        public Builder firstName(String value) { this.firstName = value; return this; }
        public Builder middleName(String value) { this.middleName = value; return this; }
        public Builder lastName(String value) { this.lastName = value; return this; }
        public Builder secondLastName(String value) { this.secondLastName = value; return this; }
        public Builder gender(Gender value) { this.gender = value; return this; }
        public Builder birthDate(LocalDate value) { this.birthDate = value; return this; }
        public Builder address(String value) { this.address = value; return this; }
        public Builder phone(String value) { this.phone = value; return this; }
        public Builder email(String value) { this.email = value; return this; }
        public Builder bloodType(String value) { this.bloodType = value; return this; }
        public Builder allergies(String value) { this.allergies = value; return this; }
        public Builder medicalConditions(String value) { this.medicalConditions = value; return this; }
        public Builder admissionDate(LocalDate value) { this.admissionDate = value; return this; }
        public Builder status(StudentStatus value) { this.status = value; return this; }

        public Student build() {
            return new Student(this);
        }
    }
}
