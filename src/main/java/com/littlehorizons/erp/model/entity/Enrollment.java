package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A student's enrollment in one grade for one academic year (table matriculas). It is an
 * ASSOCIATION: the enrollment refers to an existing Student and an existing GradeLevel, it does
 * not own them (they keep existing if the enrollment is deleted).
 */
public class Enrollment {

    public static final int MIN_YEAR = 2000;

    private Integer id;
    private final Student student;
    private final GradeLevel gradeLevel;
    private final int academicYear;
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;
    private LocalDate enrollmentDate;
    private LocalDate withdrawalDate;
    private String notes;

    public Enrollment(Student student, GradeLevel gradeLevel, int academicYear, LocalDate enrollmentDate) {
        this.student = Objects.requireNonNull(student, "student");
        this.gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel");
        int maxYear = LocalDate.now().getYear() + 1;
        if (academicYear < MIN_YEAR || academicYear > maxYear) {
            throw new ValidationException("El año académico debe estar entre " + MIN_YEAR + " y " + maxYear + ".");
        }
        this.academicYear = academicYear;
        if (enrollmentDate == null) {
            throw new ValidationException("La fecha de matrícula es obligatoria.");
        }
        this.enrollmentDate = enrollmentDate;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Student getStudent() { return student; }
    public GradeLevel getGradeLevel() { return gradeLevel; }
    public int getAcademicYear() { return academicYear; }
    public EnrollmentStatus getStatus() { return status; }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public LocalDate getWithdrawalDate() { return withdrawalDate; }
    public String getNotes() { return notes; }

    public void setStatus(EnrollmentStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void setWithdrawalDate(LocalDate withdrawalDate) {
        if (withdrawalDate != null && withdrawalDate.isBefore(enrollmentDate)) {
            throw new ValidationException("La fecha de retiro no puede ser anterior a la matrícula.");
        }
        this.withdrawalDate = withdrawalDate;
    }

    public void setNotes(String notes) {
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}
