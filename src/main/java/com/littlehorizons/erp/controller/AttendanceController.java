package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.AttendanceDao;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.AttendanceStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rules for registering and correcting the daily attendance. Who may register is decided by
 * ClassAccessPolicy (the same rule used by the grades); here there is one record per student,
 * subject and day, and a second attempt must be a correction. The period of the registration is
 * the one that contains the date. Reports are in AttendanceReportController.
 */
public class AttendanceController extends BaseController {

    private final AttendanceDao attendanceDao;
    private final SubjectRegistrationDao registrationDao;
    private final ClassAccessPolicy access;

    public AttendanceController(AttendanceDao attendanceDao, SubjectRegistrationDao registrationDao,
                                ClassAccessPolicy access, ErrorLogger logger) {
        super(logger);
        this.attendanceDao = attendanceDao;
        this.registrationDao = registrationDao;
        this.access = access;
    }

    public Result<AttendanceRecord> record(Teacher teacher, Student student, Subject subject, LocalDate date,
                                           AttendanceStatus status, String observations) {
        return execute("Register attendance", () -> {
            AttendanceRecord.checkSchoolDay(date);
            Optional<String> denied = access.denialReason(teacher, student, subject, date.getYear(),
                    AcademicPeriod.ofDate(date));
            if (denied.isPresent()) {
                return Result.fail(denied.get());
            }
            Optional<AttendanceRecord> existing = attendanceDao.find(student.getId(), subject.getId(), date);
            if (existing.isPresent()) {
                return Result.fail("Ya hay asistencia de " + student.getFullName() + " en " + subject.getName()
                        + " el " + date + " (" + existing.get().getStatus().getDisplayName()
                        + "). Use «Corregir una asistencia».");
            }
            AttendanceRecord record = new AttendanceRecord(student, subject, date, status,
                    teacher.getId(), teacher.getFullName());
            record.setObservations(observations);
            attendanceDao.create(record);
            return Result.ok(record, "Asistencia registrada: " + student.getFullName() + " - " + subject.getName()
                    + " - " + date + ": " + status.getDisplayName() + ".");
        });
    }

    public Result<AttendanceRecord> correct(Teacher teacher, AttendanceRecord record, AttendanceStatus newStatus,
                                            String observations) {
        return execute("Correct attendance", () -> {
            Optional<String> denied = access.denialReason(teacher, record.getStudent(), record.getSubject(),
                    record.getDate().getYear(), AcademicPeriod.ofDate(record.getDate()));
            if (denied.isPresent()) {
                return Result.fail(denied.get());
            }
            AttendanceStatus previous = record.getStatus();
            record.setStatus(newStatus);
            record.setObservations(observations);
            record.setRegisteredBy(teacher.getId(), teacher.getFullName());
            attendanceDao.update(record);
            return Result.ok(record, "Asistencia corregida: " + previous.getDisplayName() + " → "
                    + newStatus.getDisplayName() + ".");
        });
    }

    /** What the student has on that day in the subjects the teacher is allowed to correct. */
    public Result<List<AttendanceRecord>> correctableRecords(Teacher teacher, Student student, LocalDate date) {
        return execute("List correctable attendance", () -> {
            List<AttendanceRecord> correctable = new ArrayList<>();
            for (AttendanceRecord record : attendanceDao.findByStudentAndDate(student.getId(), date)) {
                if (access.denialReason(teacher, record.getStudent(), record.getSubject(),
                        date.getYear(), AcademicPeriod.ofDate(date)).isEmpty()) {
                    correctable.add(record);
                }
            }
            return Result.ok(correctable, "");
        });
    }

    /** The students of the group of an assignment on a date, with the attendance already registered. */
    public Result<List<AttendanceRosterEntry>> roster(TeachingAssignment assignment, LocalDate date) {
        return execute("Load attendance roster", () -> {
            AttendanceRecord.checkSchoolDay(date);
            if (date.getYear() != assignment.getAcademicYear()) {
                return Result.fail("La fecha debe estar en el año de la asignación (" + assignment.getAcademicYear() + ").");
            }
            Subject subject = assignment.getSubject();
            Map<Integer, AttendanceRecord> existing = attendanceDao.findBySubjectAndDate(subject.getId(), date).stream()
                    .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity(), (a, b) -> a));
            List<AttendanceRosterEntry> roster = registrationDao.findBySubjectYearPeriodAndSection(subject.getId(),
                            date.getYear(), AcademicPeriod.ofDate(date), assignment.getSection()).stream()
                    .map(r -> new AttendanceRosterEntry(r.getStudent(), existing.get(r.getStudent().getId())))
                    .toList();
            return Result.ok(roster, "");
        });
    }
}
