package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.GuardianDao;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business rules of student management: a student needs at least one guardian and exactly one
 * main guardian, documents cannot repeat, a retired student cannot be edited, and a guardian
 * cannot be removed if that would leave the student alone. The view only asks; the DAO only
 * saves; the rules live here.
 */
public class StudentController extends BaseController {

    private final StudentDao studentDao;
    private final GuardianDao guardianDao;

    public StudentController(StudentDao studentDao, GuardianDao guardianDao, ErrorLogger logger) {
        super(logger);
        this.studentDao = studentDao;
        this.guardianDao = guardianDao;
    }

    public Result<Student> register(Student student, List<StudentGuardian> guardians) {
        return execute("Register student", () -> {
            if (guardians == null || guardians.isEmpty()) {
                throw new ValidationException("El estudiante debe tener al menos un acudiente.");
            }
            if (studentDao.findByDocumentNumber(student.getDocumentNumber()).isPresent()) {
                return Result.fail("Ya existe un estudiante con el documento " + student.getDocumentNumber() + ".");
            }
            if (guardians.stream().noneMatch(StudentGuardian::isPrincipal)) {
                guardians.get(0).setPrincipal(true);
            }
            long principals = guardians.stream().filter(StudentGuardian::isPrincipal).count();
            if (principals > 1) {
                throw new ValidationException("Solo un acudiente puede ser el principal.");
            }
            studentDao.create(student, guardians);
            return Result.ok(student, "Estudiante registrado con código " + student.getId() + ".");
        });
    }

    public Result<Student> update(Student student) {
        return execute("Update student", () -> {
            if (student.getStatus() == StudentStatus.WITHDRAWN) {
                return Result.fail("Un estudiante retirado no se puede modificar.");
            }
            studentDao.update(student);
            return Result.ok(student, "Datos del estudiante actualizados.");
        });
    }

    public Result<Void> retire(Student student) {
        return execute("Retire student", () -> {
            if (student.getStatus() == StudentStatus.WITHDRAWN) {
                return Result.fail("El estudiante ya está retirado.");
            }
            studentDao.retire(student.getId(), LocalDate.now());
            student.setStatus(StudentStatus.WITHDRAWN);
            return Result.ok("Estudiante retirado. Sus matrículas e inscripciones activas fueron cerradas.");
        });
    }

    public Result<List<Student>> listAll() {
        return execute("List students", () -> Result.ok(studentDao.findAll(), ""));
    }

    public Result<List<Student>> search(String term) {
        return execute("Search students", () -> {
            if (term == null || term.isBlank()) {
                throw new ValidationException("Escriba un nombre, apellido o documento para buscar.");
            }
            return Result.ok(studentDao.search(term.trim()), "");
        });
    }

    public Result<Student> findByDocument(String documentNumber) {
        return execute("Find student", () -> studentDao.findByDocumentNumber(documentNumber)
                .map(student -> Result.ok(student, ""))
                .orElseGet(() -> Result.fail("No existe un estudiante con el documento " + documentNumber + ".")));
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
