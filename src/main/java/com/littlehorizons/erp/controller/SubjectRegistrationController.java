package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.dao.SubjectDao;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;
import com.littlehorizons.erp.model.enums.RegistrationStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;

/**
 * Rules for registering students in subjects: the student needs an ACTIVE enrollment in that
 * year, the subject must belong to the grade of that enrollment, and the same subject cannot be
 * registered twice in the same period.
 */
public class SubjectRegistrationController extends BaseController {

    private final SubjectRegistrationDao registrationDao;
    private final SubjectDao subjectDao;
    private final EnrollmentDao enrollmentDao;

    public SubjectRegistrationController(SubjectRegistrationDao registrationDao, SubjectDao subjectDao,
                                         EnrollmentDao enrollmentDao, ErrorLogger logger) {
        super(logger);
        this.registrationDao = registrationDao;
        this.subjectDao = subjectDao;
        this.enrollmentDao = enrollmentDao;
    }

    /** Subjects the student can take: those of the grade of its active enrollment in that year. */
    public Result<List<Subject>> availableSubjects(Student student, int academicYear) {
        return execute("List available subjects", () -> {
            Enrollment enrollment = activeEnrollment(student, academicYear);
            if (enrollment == null) {
                return Result.fail("El estudiante no tiene matrícula activa en " + academicYear + ".");
            }
            return Result.ok(subjectDao.findActiveByGrade(enrollment.getGradeLevel().getId()), "");
        });
    }

    public Result<SubjectRegistration> register(Student student, Subject subject, int academicYear,
                                                AcademicPeriod period, String section) {
        return execute("Register in subject", () -> {
            Enrollment enrollment = activeEnrollment(student, academicYear);
            if (enrollment == null) {
                return Result.fail("El estudiante no tiene matrícula activa en " + academicYear + ".");
            }
            if (enrollment.getGradeLevel().getId() != subject.getGradeLevel().getId()) {
                return Result.fail("La materia " + subject.getCode() + " pertenece a "
                        + subject.getGradeLevel().getName() + ", no al grado del estudiante ("
                        + enrollment.getGradeLevel().getName() + ").");
            }
            if (registrationDao.exists(student.getId(), subject.getId(), academicYear, period)) {
                return Result.fail("Ya está inscrito en " + subject.getName() + " en el período " + period.getDbLabel() + ".");
            }
            SubjectRegistration registration =
                    new SubjectRegistration(student, subject, academicYear, period, LocalDate.now());
            registration.setSection(section);
            registrationDao.create(registration);
            return Result.ok(registration, "Inscripción registrada en " + subject.getName() + ".");
        });
    }

    /** Registers the student in every subject of its grade that it does not have yet in that period. */
    public Result<Integer> registerAll(Student student, int academicYear, AcademicPeriod period) {
        return execute("Register in all subjects", () -> {
            Enrollment enrollment = activeEnrollment(student, academicYear);
            if (enrollment == null) {
                return Result.fail("El estudiante no tiene matrícula activa en " + academicYear + ".");
            }
            int created = 0;
            for (Subject subject : subjectDao.findActiveByGrade(enrollment.getGradeLevel().getId())) {
                if (!registrationDao.exists(student.getId(), subject.getId(), academicYear, period)) {
                    registrationDao.create(new SubjectRegistration(student, subject, academicYear, period, LocalDate.now()));
                    created++;
                }
            }
            return Result.ok(created, created + " inscripción(es) nueva(s) en el período " + period.getDbLabel() + ".");
        });
    }

    public Result<List<SubjectRegistration>> byStudent(Student student, int academicYear) {
        return execute("List registrations", () -> Result.ok(registrationDao.findByStudentAndYear(student.getId(), academicYear), ""));
    }

    public Result<Void> withdraw(SubjectRegistration registration) {
        return execute("Withdraw from subject", () -> {
            if (registration.getStatus() != RegistrationStatus.ACTIVE) {
                return Result.fail("Solo se puede retirar una inscripción activa.");
            }
            registrationDao.updateStatus(registration.getId(), RegistrationStatus.WITHDRAWN);
            return Result.ok("Inscripción retirada.");
        });
    }

    private Enrollment activeEnrollment(Student student, int academicYear) throws com.littlehorizons.erp.exception.DaoException {
        return enrollmentDao.findByStudentAndYear(student.getId(), academicYear)
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .orElse(null);
    }
}
