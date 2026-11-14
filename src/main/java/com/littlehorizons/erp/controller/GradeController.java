package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.grading.CourseSubjectAverage;
import com.littlehorizons.erp.controller.grading.FinalGradeStrategy;
import com.littlehorizons.erp.controller.grading.GradeAverages;
import com.littlehorizons.erp.controller.grading.YearReport;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.AcademicRecordDao;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.dao.TeachingAssignmentDao;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;
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
 * Rules for registering and consulting grades. A grade can only be registered by an ACTIVE
 * teacher who teaches that subject in that year (and in the student's section, when known), for a
 * student who is registered in the subject in that period and has not withdrawn. There is one
 * grade per student, subject, year and period: a second attempt must be a correction.
 */
public class GradeController extends BaseController {

    private final AcademicRecordDao recordDao;
    private final SubjectRegistrationDao registrationDao;
    private final TeachingAssignmentDao assignmentDao;
    private final Supplier<FinalGradeStrategy> strategySupplier;
    private FinalGradeStrategy finalGradeStrategy; // resolved on first use

    public GradeController(AcademicRecordDao recordDao, SubjectRegistrationDao registrationDao,
                           TeachingAssignmentDao assignmentDao, Supplier<FinalGradeStrategy> strategySupplier,
                           ErrorLogger logger) {
        super(logger);
        this.recordDao = recordDao;
        this.registrationDao = registrationDao;
        this.assignmentDao = assignmentDao;
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

    // ---- registering ----

    public Result<AcademicRecord> record(Teacher teacher, Student student, Subject subject, int academicYear,
                                         AcademicPeriod period, BigDecimal grade, String observations) {
        return execute("Register grade", () -> {
            String problem = authorizationProblem(teacher, student, subject, academicYear, period);
            if (problem != null) {
                return Result.fail(problem);
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
            String problem = authorizationProblem(teacher, record.getStudent(), record.getSubject(),
                    record.getAcademicYear(), record.getPeriod());
            if (problem != null) {
                return Result.fail(problem);
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
            String problem = authorizationProblem(teacher, student, subject, academicYear, AcademicPeriod.FINAL);
            if (problem != null) {
                return Result.fail(problem);
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

    /** The shared rule: who may grade what. Returns the message to show, or null if it is allowed. */
    private String authorizationProblem(Teacher teacher, Student student, Subject subject, int academicYear,
                                        AcademicPeriod period) throws DaoException {
        if (teacher.getStatus() != TeacherStatus.ACTIVE) {
            return "Solo un profesor con estado Activo puede registrar notas.";
        }
        // The Final grade belongs to the whole year: it needs the student to be in the last period.
        AcademicPeriod registrationPeriod = period == AcademicPeriod.FINAL ? AcademicPeriod.FOURTH : period;
        Optional<SubjectRegistration> registration = registrationDao
                .findByStudentAndYear(student.getId(), academicYear).stream()
                .filter(r -> r.getSubject().getId() == subject.getId() && r.getPeriod() == registrationPeriod)
                .findFirst();
        if (registration.isEmpty()) {
            return student.getFullName() + " no está inscrito en " + subject.getName() + " en "
                    + registrationPeriod.getDisplayName() + " de " + academicYear + ".";
        }
        if (registration.get().getStatus() == RegistrationStatus.WITHDRAWN) {
            return student.getFullName() + " se retiró de " + subject.getName() + " en "
                    + registrationPeriod.getDisplayName() + "; no se le pueden registrar notas ahí.";
        }
        String section = registration.get().getSection();
        boolean teaches = assignmentDao.findByTeacherAndYear(teacher.getId(), academicYear).stream()
                .anyMatch(a -> a.getSubject().getId() == subject.getId()
                        && (section == null || a.getSection().equalsIgnoreCase(section)));
        if (!teaches) {
            return teacher.getFullName() + " no tiene asignada " + subject.getName() + " en " + academicYear
                    + (section == null ? "" : " para el grupo " + section) + ".";
        }
        return null;
    }

    // ---- group roster ----

    /** The students of the group of an assignment in a period, with the grade they already have. */
    public Result<List<RosterEntry>> roster(TeachingAssignment assignment, AcademicPeriod period) {
        return execute("Load group roster", () -> {
            AcademicPeriod registrationPeriod = period == AcademicPeriod.FINAL ? AcademicPeriod.FOURTH : period;
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
                if (authorizationProblem(teacher, record.getStudent(), record.getSubject(),
                        record.getAcademicYear(), record.getPeriod()) == null) {
                    correctable.add(record);
                }
            }
            return Result.ok(correctable, "");
        });
    }

    // ---- consulting ----

    public Result<YearReport> yearReport(Student student, int academicYear) {
        return execute("Year report", () -> {
            List<AcademicRecord> records = recordDao.findByStudentAndYear(student.getId(), academicYear);
            if (records.isEmpty()) {
                return Result.fail(student.getFullName() + " no tiene notas registradas en " + academicYear + ".");
            }
            return Result.ok(GradeAverages.yearReport(academicYear, records), "");
        });
    }

    public Result<List<YearReport>> history(Student student) {
        return execute("Academic history", () -> {
            List<AcademicRecord> records = recordDao.findByStudent(student.getId());
            if (records.isEmpty()) {
                return Result.fail(student.getFullName() + " no tiene notas registradas.");
            }
            return Result.ok(GradeAverages.history(records), "");
        });
    }

    public Result<List<CourseSubjectAverage>> courseAverages(GradeLevel grade, int academicYear, AcademicPeriod period) {
        return execute("Course averages", () -> {
            List<CourseSubjectAverage> averages = GradeAverages.courseAverages(
                    recordDao.findByGradeAndYear(grade.getId(), academicYear), period);
            if (averages.isEmpty()) {
                return Result.fail("No hay notas de " + grade.getName() + " en " + period.getDisplayName() + " de " + academicYear + ".");
            }
            return Result.ok(averages, "");
        });
    }
}
