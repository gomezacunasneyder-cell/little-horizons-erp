package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.dao.TeachingAssignmentDao;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;

import java.util.Optional;

/**
 * The rule "who may register something about a student in a class", in one place (Single
 * Responsibility, and DRY: grades and attendance share it). Controllers decide what to do; this
 * class only answers whether the teacher is allowed. The teacher must be ACTIVE and teach the
 * subject in that year (in the student's section, when the registration has one), and the
 * student must be registered in the subject in that period without having withdrawn.
 */
public final class ClassAccessPolicy {

    private final SubjectRegistrationDao registrationDao;
    private final TeachingAssignmentDao assignmentDao;

    public ClassAccessPolicy(SubjectRegistrationDao registrationDao, TeachingAssignmentDao assignmentDao) {
        this.registrationDao = registrationDao;
        this.assignmentDao = assignmentDao;
    }

    /**
     * Empty if the teacher may register; otherwise the message to show to the user. The period is
     * the one of the registration (I to IV), never Final.
     */
    public Optional<String> denialReason(Teacher teacher, Student student, Subject subject,
                                         int academicYear, AcademicPeriod registrationPeriod) throws DaoException {
        if (teacher.getStatus() != TeacherStatus.ACTIVE) {
            return Optional.of("Solo un profesor con estado Activo puede hacer este registro.");
        }
        Optional<SubjectRegistration> registration = registrationDao
                .findByStudentAndYear(student.getId(), academicYear).stream()
                .filter(r -> r.getSubject().getId() == subject.getId() && r.getPeriod() == registrationPeriod)
                .findFirst();
        if (registration.isEmpty()) {
            return Optional.of(student.getFullName() + " no está inscrito en " + subject.getName() + " en "
                    + registrationPeriod.getDisplayName() + " de " + academicYear + ".");
        }
        if (registration.get().getStatus() == RegistrationStatus.WITHDRAWN) {
            return Optional.of(student.getFullName() + " se retiró de " + subject.getName() + " en "
                    + registrationPeriod.getDisplayName() + "; no se le pueden hacer registros ahí.");
        }
        String section = registration.get().getSection();
        boolean teaches = assignmentDao.findByTeacherAndYear(teacher.getId(), academicYear).stream()
                .anyMatch(a -> a.getSubject().getId() == subject.getId()
                        && (section == null || a.getSection().equalsIgnoreCase(section)));
        if (!teaches) {
            return Optional.of(teacher.getFullName() + " no tiene asignada " + subject.getName() + " en "
                    + academicYear + (section == null ? "" : " para el grupo " + section) + ".");
        }
        return Optional.empty();
    }
}
