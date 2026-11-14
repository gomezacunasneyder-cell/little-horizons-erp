package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.TeacherStatus;

import java.time.LocalDate;

/**
 * A teacher of the school. Like Student and Guardian it extends Person (same identity data and
 * validations) and adds what only a teacher has: a mandatory e-mail (it is his institutional
 * contact and is unique in the database), the specialization, the hiring date and a status.
 */
public class Teacher extends Person {

    private static final int SPECIALIZATION_MAX = 100;
    private static final int MIN_HIRE_YEAR = 1950;

    private String specialization;
    private LocalDate hireDate;
    private TeacherStatus status = TeacherStatus.ACTIVE;

    public Teacher(DocumentType documentType, String documentNumber, String firstName, String lastName,
                   String email, String specialization, LocalDate hireDate) {
        super(documentType, documentNumber, firstName, lastName);
        if (documentType == DocumentType.TI) {
            throw new ValidationException("Un profesor no puede tener tarjeta de identidad (use CC, CE o Pasaporte).");
        }
        setEmail(email);
        setSpecialization(specialization);
        setHireDate(hireDate);
    }

    /** The e-mail is mandatory for a teacher; the check runs before changing the current value. */
    @Override
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("El correo es obligatorio para un profesor.");
        }
        super.setEmail(email);
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = requireLabel(specialization, SPECIALIZATION_MAX, "La especialización");
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        if (hireDate == null) {
            throw new ValidationException("La fecha de contratación es obligatoria.");
        }
        if (hireDate.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de contratación no puede estar en el futuro.");
        }
        if (hireDate.getYear() < MIN_HIRE_YEAR) {
            throw new ValidationException("La fecha de contratación no es válida.");
        }
        this.hireDate = hireDate;
    }

    public TeacherStatus getStatus() {
        return status;
    }

    public void setStatus(TeacherStatus status) {
        this.status = status == null ? TeacherStatus.ACTIVE : status;
    }
}
