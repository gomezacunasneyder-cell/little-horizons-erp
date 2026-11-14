package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.grading.FinalGradeStrategy;
import com.littlehorizons.erp.model.dao.AcademicRecordDao;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.util.ErrorLogger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Rules for registering and correcting grades. Who may grade what is decided by
 * ClassAccessPolicy; here there is one grade per student, subject, year and period (a
 * second attempt must be a correction) and the Final grade is computed with the configured
 * FinalGradeStrategy. Consulting reports is the job of GradeReportController.
 */
public class GradeController extends BaseController {

    private final AcademicRecordDao recordDao;
    private final SubjectRegistrationDao registrationDao;
    private final ClassAccessPolicy access;
    private final Supplier<FinalGradeStrategy> strategySupplier;
    private FinalGradeStrategy finalGradeStrategy; // resolved on first use

    public GradeController(AcademicRecordDao recordDao, SubjectRegistrationDao registrationDao,
                           ClassAccessPolicy access, Supplier<FinalGradeStrategy> strategySupplier,
                           ErrorLogger logger) {
        super(logger);
        this.recordDao = recordDao;
        this.registrationDao = registrationDao;
        this.access = access;
        this.strategySupplier = strategySupplier;
    }

    /**
     * The policy is read from config.txt, which may be wrong. It is resolved the first time a
     * Final grade is needed, so a bad value gives a message there (see BaseController) instead of
     * stopping the whole program at startup.
     */
    private FinalGradeStrategy strategy() {
        if (finalGradeStrategy == null) {
            finalGradeStrategy = strategySupplier.get();
        }
        return finalGradeStrategy;
    }

    public String finalGradePolicy() {
        return strategy().getName();
    }

    /** The Final grade belongs to the whole year: it is checked against the registration of period IV. */
    private static AcademicPeriod registrationPeriod(AcademicPeriod gradePeriod) {
        return gradePeriod == AcademicPeriod.FINAL ? AcademicPeriod.FOURTH : gradePeriod;
    }

    // ---- registering ----

    public Result<AcademicRecord> record(Teacher teacher, Student student, Subject subject, int academicYear,
                                         AcademicPeriod period, BigDecimal grade, String observations) {
        return execute("Register grade", () -> {
            Optional<String> denied = access.denialReason(teacher, student, subject, academicYear, registrationPeriod(period));
            if (denied.isPresent()) {
                return Result.fail(denied.get());
            }
            Optional<AcademicRecord> existing = recordDao.find(student.getId(), subject.getId(), academicYear, period);
            if (existing.isPresent()) {
                return Result.fail("Ya existe una nota de " + subject.getName() + " en " + period.getDisplayName()
                        + " " + academicYear + " (" + existing.get().getGrade() + "). Use «Corregir una nota».");
            }
            AcademicRecord record = new AcademicRecord(student, subject, academicYear, period, grade,
                    teacher.getId(), teacher.getFullName(), LocalDate.now());
            record.setObservations(observations);
            recordDao.create(record);
            return Result.ok(record, "Nota registrada: " + student.getFullName() + " - " + subject.getName()
                    + " - " + period.getDisplayName() + ": " + record.getGrade() + ".");
        });
    }

    public Result<AcademicRecord> correct(Teacher teacher, AcademicRecord record, BigDecimal newGrade, String observations) {
        return execute("Correct grade", () -> {
            Optional<String> denied = access.denialReason(teacher, record.getStudent(), record.getSubject(),
                    record.getAcademicYear(), registrationPeriod(record.getPeriod()));
            if (denied.isPresent()) {
                return Result.fail(denied.get());
            }
            BigDecimal previous = record.getGrade();
            record.setGrade(newGrade);
            record.setObservations(observations);
            record.setRegisteredBy(teacher.getId(), teacher.getFullName(), LocalDate.now());
            recordDao.update(record);
            return Result.ok(record, "Nota corregida: " + previous + " → " + record.getGrade() + ".");
        });
    }

    /** Computes the Final grade of a subject with the configured strategy from the periods I-IV. */
    public Result<AcademicRecord> generateFinal(Teacher teacher, Student student, Subject subject, int academicYear) {
        return execute("Generate final grade", () -> {
            Optional<String> denied = access.denialReason(teacher, student, subject, academicYear, AcademicPeriod.FOURTH);
            if (denied.isPresent()) {
                return Result.fail(denied.get());
            }
            Map<AcademicPeriod, BigDecimal> grades = new EnumMap<>(AcademicPeriod.class);
            recordDao.findByStudentAndYear(student.getId(), academicYear).stream()
                    .filter(r -> r.getSubject().getId() == subject.getId())
                    .forEach(r -> grades.put(r.getPeriod(), r.getGrade()));
            String missing = AcademicPeriod.teachingPeriods().stream()
                    .filter(p -> !grades.containsKey(p))
                    .map(AcademicPeriod::getDbLabel)
                    .collect(Collectors.joining(", "));
            if (!missing.isEmpty()) {
                return Result.fail("Faltan las notas de los períodos: " + missing + ". Se necesitan I, II, III y IV.");
            }
            FinalGradeStrategy policy = strategy();
            BigDecimal finalGrade = policy.compute(grades);
            AcademicRecord record = recordDao
                    .find(student.getId(), subject.getId(), academicYear, AcademicPeriod.FINAL).orElse(null);
            if (record == null) {
                record = new AcademicRecord(student, subject, academicYear, AcademicPeriod.FINAL, finalGrade,
                        teacher.getId(), teacher.getFullName(), LocalDate.now());
                recordDao.create(record);
            } else {
                record.setGrade(finalGrade);
                record.setRegisteredBy(teacher.getId(), teacher.getFullName(), LocalDate.now());
                recordDao.update(record);
            }
            return Result.ok(record, "Nota final de " + subject.getName() + ": " + finalGrade
                    + " (" + policy.getName() + ").");
        });
    }

    // ---- group roster ----

    /** The students of the group of an assignment in a period, with the grade they already have. */
    public Result<List<RosterEntry>> roster(TeachingAssignment assignment, AcademicPeriod period) {
        return execute("Load group roster", () -> {
            AcademicPeriod registrationPeriod = registrationPeriod(period);
            Subject subject = assignment.getSubject();
            Map<Integer, AcademicRecord> existing = recordDao
                    .findBySubjectYearAndPeriod(subject.getId(), assignment.getAcademicYear(), period).stream()
                    .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity(), (a, b) -> a));
            List<RosterEntry> roster = registrationDao.findBySubjectYearPeriodAndSection(subject.getId(),
                            assignment.getAcademicYear(), registrationPeriod, assignment.getSection()).stream()
                    .map(r -> new RosterEntry(r.getStudent(), existing.get(r.getStudent().getId())))
                    .toList();
            return Result.ok(roster, "");
        });
    }

    /** Grades of a student, in the year, that the teacher is allowed to correct (same rule as {@link #correct}). */
    public Result<List<AcademicRecord>> correctableRecords(Teacher teacher, Student student, int academicYear) {
        return execute("List correctable grades", () -> {
            List<AcademicRecord> correctable = new ArrayList<>();
            for (AcademicRecord record : recordDao.findByStudentAndYear(student.getId(), academicYear)) {
                if (access.denialReason(teacher, record.getStudent(), record.getSubject(),
                        record.getAcademicYear(), registrationPeriod(record.getPeriod())).isEmpty()) {
                    correctable.add(record);
                }
            }
            return Result.ok(correctable, "");
        });
    }
}
