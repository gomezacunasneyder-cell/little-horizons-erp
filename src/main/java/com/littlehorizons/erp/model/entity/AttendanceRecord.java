package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.AttendanceStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The attendance of one student in one subject on one day. There is at most one record per
 * student, subject and date (the database also enforces it). The date must be a school day
 * (Monday to Friday) that has already happened.
 */
public class AttendanceRecord {

    public static final int OBSERVATIONS_MAX = 500;

    private Integer id;
    private final Student student;
    private final Subject subject;
    private final LocalDate date;
    private AttendanceStatus status;
    private String observations;
    private int teacherId;
    private String teacherName;

    public AttendanceRecord(Student student, Subject subject, LocalDate date, AttendanceStatus status,
                            int teacherId, String teacherName) {
        this.student = Objects.requireNonNull(student, "student");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.date = checkSchoolDay(date);
        setStatus(status);
        setRegisteredBy(teacherId, teacherName);
    }

    /** Used by the view and the constructor so the rule is written once. */
    public static LocalDate checkSchoolDay(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha es obligatoria.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("No se puede registrar asistencia de una fecha futura.");
        }
        if (date.getYear() < Enrollment.MIN_YEAR) {
            throw new ValidationException("El año debe ser " + Enrollment.MIN_YEAR + " o posterior.");
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new ValidationException("Solo hay clases de lunes a viernes.");
        }
        return date;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Student getStudent() { return student; }
    public Subject getSubject() { return subject; }
    public LocalDate getDate() { return date; }
    public AttendanceStatus getStatus() { return status; }
    public String getObservations() { return observations; }
    public int getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; }

    public void setStatus(AttendanceStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Optional note (for example the medical certificate). Blank means no note. */
    public void setObservations(String observations) {
        if (observations == null || observations.isBlank()) {
            this.observations = null;
            return;
        }
        String clean = observations.trim();
        if (clean.length() > OBSERVATIONS_MAX) {
            throw new ValidationException("Las observaciones admiten máximo " + OBSERVATIONS_MAX + " caracteres.");
        }
        this.observations = clean;
    }

    /** The teacher who registered (or last corrected) the attendance. */
    public void setRegisteredBy(int teacherId, String teacherName) {
        if (teacherName == null || teacherName.isBlank()) {
            throw new ValidationException("Falta el profesor que registra la asistencia.");
        }
        this.teacherId = teacherId;
        this.teacherName = teacherName;
    }

    @Override
    public String toString() {
        return date + " - " + subject.getName() + ": " + status.getDisplayName();
    }
}
