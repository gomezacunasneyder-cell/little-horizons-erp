package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One grade of a student in a subject, for one academic year and period (table
 * registros_academicos). The grade is a BigDecimal with exactly one decimal between 0.0 and 5.0,
 * the Colombian scale; BigDecimal is used instead of double so that 3.7 is really 3.7 and the
 * averages are exact. The teacher who registered it is kept only as id and name because that is
 * all the screens need.
 */
public class AcademicRecord {

    public static final BigDecimal MIN_GRADE = new BigDecimal("0.0");
    public static final BigDecimal MAX_GRADE = new BigDecimal("5.0");
    public static final Pattern GRADE_PATTERN = Pattern.compile("[0-5](?:[.,][0-9])?");
    public static final String GRADE_MESSAGE = "La nota debe estar entre 0.0 y 5.0 con un solo decimal (ejemplo: 4.2).";
    public static final int OBSERVATIONS_MAX = 500;

    private Integer id;
    private final Student student;
    private final Subject subject;
    private final int academicYear;
    private final AcademicPeriod period;
    private BigDecimal grade;
    private String observations;
    private int teacherId;
    private String teacherName;
    private LocalDate recordDate;

    public AcademicRecord(Student student, Subject subject, int academicYear, AcademicPeriod period,
                          BigDecimal grade, int teacherId, String teacherName, LocalDate recordDate) {
        this.student = Objects.requireNonNull(student, "student");
        this.subject = Objects.requireNonNull(subject, "subject");
        if (academicYear < Enrollment.MIN_YEAR || academicYear > LocalDate.now().getYear() + 1) {
            throw new ValidationException("El año académico debe estar entre " + Enrollment.MIN_YEAR
                    + " y " + (LocalDate.now().getYear() + 1) + ".");
        }
        this.academicYear = academicYear;
        this.period = Objects.requireNonNull(period, "period");
        setGrade(grade);
        setRegisteredBy(teacherId, teacherName, recordDate);
    }

    /** Converts what the user typed ("4.2" or "4,2") into a valid grade. */
    public static BigDecimal parseGrade(String text) {
        if (text == null || !GRADE_PATTERN.matcher(text.trim()).matches()) {
            throw new ValidationException(GRADE_MESSAGE);
        }
        BigDecimal value = new BigDecimal(text.trim().replace(',', '.'));
        validateRange(value);
        return value.setScale(1, RoundingMode.UNNECESSARY);
    }

    private static void validateRange(BigDecimal value) {
        if (value.compareTo(MIN_GRADE) < 0 || value.compareTo(MAX_GRADE) > 0) {
            throw new ValidationException(GRADE_MESSAGE);
        }
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Student getStudent() { return student; }
    public Subject getSubject() { return subject; }
    public int getAcademicYear() { return academicYear; }
    public AcademicPeriod getPeriod() { return period; }
    public BigDecimal getGrade() { return grade; }
    public String getObservations() { return observations; }
    public int getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; }
    public LocalDate getRecordDate() { return recordDate; }

    public void setGrade(BigDecimal grade) {
        if (grade == null) {
            throw new ValidationException("La nota es obligatoria.");
        }
        if (grade.scale() > 1 && grade.stripTrailingZeros().scale() > 1) {
            throw new ValidationException(GRADE_MESSAGE);
        }
        validateRange(grade);
        this.grade = grade.setScale(1, RoundingMode.UNNECESSARY);
    }

    public void setObservations(String observations) {
        if (observations == null || observations.isBlank()) {
            this.observations = null;
            return;
        }
        if (observations.trim().length() > OBSERVATIONS_MAX) {
            throw new ValidationException("Las observaciones admiten máximo " + OBSERVATIONS_MAX + " caracteres.");
        }
        this.observations = observations.trim();
    }

    /** Who registered (or last corrected) the grade, and when. */
    public void setRegisteredBy(int teacherId, String teacherName, LocalDate recordDate) {
        if (recordDate == null || recordDate.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de registro no puede estar en el futuro.");
        }
        this.teacherId = teacherId;
        this.teacherName = Objects.requireNonNull(teacherName, "teacherName");
        this.recordDate = recordDate;
    }

    @Override
    public String toString() {
        return subject.getName() + " - " + academicYear + " " + period.getDisplayName() + ": " + grade;
    }
}
