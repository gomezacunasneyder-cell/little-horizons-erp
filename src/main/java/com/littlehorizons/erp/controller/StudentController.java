package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;

/**
 * Business rules of student management: a student needs at least one guardian and exactly one
 * main guardian, documents cannot repeat and a retired student cannot be edited. Managing the
 * guardians of an existing student is a different responsibility and lives in GuardianController.
 * The view only asks; the DAO only saves; the rules live here.
 */
public class StudentController extends BaseController {

    private final StudentDao studentDao;

    public StudentController(StudentDao studentDao, ErrorLogger logger) {
        super(logger);
        this.studentDao = studentDao;
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
}
