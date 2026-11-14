package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.GuardianDao;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;
import java.util.Optional;

/**
 * Rules for the guardians of a student: a guardian is shared by brothers and sisters, a student
 * keeps exactly one main guardian and the main guardian cannot be removed (another one must be
 * designated first). It was separated from StudentController to keep one responsibility per
 * class (Single Responsibility Principle).
 */
public class GuardianController extends BaseController {

    private final StudentDao studentDao;
    private final GuardianDao guardianDao;

    public GuardianController(StudentDao studentDao, GuardianDao guardianDao, ErrorLogger logger) {
        super(logger);
        this.studentDao = studentDao;
        this.guardianDao = guardianDao;
    }

    public Result<List<StudentGuardian>> guardiansOf(Student student) {
        return execute("List guardians", () -> Result.ok(studentDao.findGuardians(student.getId()), ""));
    }

    /** Students that share a guardian (siblings). */
    public Result<List<Student>> studentsOf(Guardian guardian) {
        return execute("List guardian's students", () -> Result.ok(studentDao.findByGuardian(guardian.getId()), ""));
    }

    /** Looks for an existing guardian so a brother or sister reuses it instead of duplicating it. */
    public Result<Optional<Guardian>> findGuardian(String documentNumber) {
        return execute("Find guardian", () -> Result.ok(guardianDao.findByDocumentNumber(documentNumber), ""));
    }

    public Result<Void> addGuardian(Student student, StudentGuardian link) {
        return execute("Add guardian", () -> {
            boolean alreadyLinked = studentDao.findGuardians(student.getId()).stream()
                    .anyMatch(existing -> existing.getGuardian().getDocumentNumber()
                            .equals(link.getGuardian().getDocumentNumber()));
            if (alreadyLinked) {
                return Result.fail("Ese acudiente ya está asociado al estudiante.");
            }
            studentDao.addGuardian(student.getId(), link);
            if (link.isPrincipal()) {
                studentDao.setPrincipalGuardian(student.getId(), link.getGuardian().getId());
            }
            return Result.ok("Acudiente asociado.");
        });
    }

    public Result<Void> removeGuardian(Student student, StudentGuardian link) {
        return execute("Remove guardian", () -> {
            if (link.isPrincipal()) {
                return Result.fail("No se puede quitar al acudiente principal. Designe otro como principal primero.");
            }
            studentDao.removeGuardian(student.getId(), link.getGuardian().getId());
            return Result.ok("Acudiente desvinculado del estudiante.");
        });
    }

    public Result<Void> changePrincipalGuardian(Student student, StudentGuardian newPrincipal) {
        return execute("Change principal guardian", () -> {
            studentDao.setPrincipalGuardian(student.getId(), newPrincipal.getGuardian().getId());
            return Result.ok("Acudiente principal actualizado.");
        });
    }

    public Result<Void> updateGuardian(Guardian guardian) {
        return execute("Update guardian", () -> {
            guardianDao.update(guardian);
            return Result.ok("Datos del acudiente actualizados.");
        });
    }
}
