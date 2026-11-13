package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The registration of a student in a subject for one academic period (table
 * inscripciones_materia). Many students per subject and many subjects per student: this class
 * is the "many-to-many" relationship made visible, with its own data (period, section, date).
 */
public class SubjectRegistration {

    private Integer id;
    private final Student student;
    private final Subject subject;
    private final int academicYear;
    private final AcademicPeriod period;
    private String section;
    private final LocalDate registrationDate;
    private RegistrationStatus status = RegistrationStatus.ACTIVE;

    public SubjectRegistration(Student student, Subject subject, int academicYear,
                               AcademicPeriod period, LocalDate registrationDate) {
        this.student = Objects.requireNonNull(student, "student");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.academicYear = academicYear;
        this.period = Objects.requireNonNull(period, "period");
        if (period == AcademicPeriod.FINAL) {
            throw new ValidationException("Solo se puede inscribir en los períodos I, II, III o IV.");
        }
        this.registrationDate = Objects.requireNonNull(registrationDate, "registrationDate");
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Student getStudent() { return student; }
    public Subject getSubject() { return subject; }
    public int getAcademicYear() { return academicYear; }
    public AcademicPeriod getPeriod() { return period; }
    public String getSection() { return section; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public RegistrationStatus getStatus() { return status; }

    public void setSection(String section) {
        this.section = section == null || section.isBlank() ? null : section.trim();
    }

    public void setStatus(RegistrationStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
