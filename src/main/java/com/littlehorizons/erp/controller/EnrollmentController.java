package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.dao.GradeLevelDao;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;

/** Rules for enrolling a student in a grade for an academic year. */
public class EnrollmentController extends BaseController {

    private final EnrollmentDao enrollmentDao;
    private final GradeLevelDao gradeLevelDao;

    public EnrollmentController(EnrollmentDao enrollmentDao, GradeLevelDao gradeLevelDao, ErrorLogger logger) {
        super(logger);
        this.enrollmentDao = enrollmentDao;
        this.gradeLevelDao = gradeLevelDao;
    }

    public Result<List<GradeLevel>> grades() {
        return execute("List grades", () -> Result.ok(gradeLevelDao.findAll(), ""));
    }

    public Result<Enrollment> enroll(Student student, GradeLevel grade, int academicYear) {
        return execute("Enroll student", () -> {
            if (student.getStatus() != StudentStatus.ACTIVE) {
                return Result.fail("Solo se pueden matricular estudiantes con estado Activo.");
            }
            if (enrollmentDao.findByStudentAndYear(student.getId(), academicYear).isPresent()) {
                return Result.fail("El estudiante ya tiene una matrícula en el año " + academicYear + ".");
            }
            Enrollment enrollment = new Enrollment(student, grade, academicYear, LocalDate.now());
            enrollmentDao.create(enrollment);
            return Result.ok(enrollment, "Matrícula registrada: " + grade.getName() + " " + academicYear + ".");
        });
    }

    public Result<List<Enrollment>> byStudent(Student student) {
        return execute("List enrollments", () -> Result.ok(enrollmentDao.findByStudent(student.getId()), ""));
    }

    public Result<List<Enrollment>> byGradeAndYear(GradeLevel grade, int academicYear) {
        return execute("List grade roster", () -> Result.ok(enrollmentDao.findByGradeAndYear(grade.getId(), academicYear), ""));
    }

    /** Closes an active enrollment as COMPLETED (year finished) or WITHDRAWN (student left). */
    public Result<Void> close(Enrollment enrollment, EnrollmentStatus newStatus) {
        return execute("Close enrollment", () -> {
            if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
                return Result.fail("Solo se puede cerrar una matrícula activa.");
            }
            if (newStatus == EnrollmentStatus.ACTIVE) {
                return Result.fail("Estado de cierre inválido.");
            }
            LocalDate withdrawal = newStatus == EnrollmentStatus.WITHDRAWN ? LocalDate.now() : null;
            enrollmentDao.updateStatus(enrollment.getId(), newStatus, withdrawal);
            return Result.ok("Matrícula marcada como " + newStatus.getDisplayName().toLowerCase() + ".");
        });
    }
}
