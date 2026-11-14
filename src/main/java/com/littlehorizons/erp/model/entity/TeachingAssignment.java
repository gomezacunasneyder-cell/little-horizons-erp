package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A teacher teaching one subject, in one section (group) of one academic year (table
 * asignaciones_profesor). It is the many-to-many relationship between teachers and subjects made
 * visible: the same subject can have several teachers (one per section) and a teacher can have
 * several subjects.
 */
public class TeachingAssignment {

    public static final Pattern SECTION_PATTERN = Pattern.compile("[A-Za-z0-9]{1,10}");
    public static final String SECTION_MESSAGE = "La sección debe tener entre 1 y 10 letras o números (ejemplo: A).";

    private Integer id;
    private final Teacher teacher;
    private final Subject subject;
    private final int academicYear;
    private final String section;

    public TeachingAssignment(Teacher teacher, Subject subject, int academicYear, String section) {
        this.teacher = Objects.requireNonNull(teacher, "teacher");
        this.subject = Objects.requireNonNull(subject, "subject");
        int maxYear = LocalDate.now().getYear() + 1;
        if (academicYear < Enrollment.MIN_YEAR || academicYear > maxYear) {
            throw new ValidationException("El año académico debe estar entre " + Enrollment.MIN_YEAR + " y " + maxYear + ".");
        }
        this.academicYear = academicYear;
        if (section == null || !SECTION_PATTERN.matcher(section.trim()).matches()) {
            throw new ValidationException(SECTION_MESSAGE);
        }
        this.section = section.trim().toUpperCase(Locale.ROOT);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Teacher getTeacher() { return teacher; }
    public Subject getSubject() { return subject; }
    public int getAcademicYear() { return academicYear; }
    public String getSection() { return section; }

    /** "Matemáticas - Quinto A", the group that receives the class. */
    public String getGroupName() {
        return subject.getGradeLevel().getName() + " " + section;
    }

    @Override
    public String toString() {
        return subject + " - " + getGroupName() + " (" + academicYear + ")";
    }
}
