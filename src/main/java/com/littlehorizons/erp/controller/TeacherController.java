package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.TeacherDao;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;

/** Rules of the teacher directory: unique document and e-mail, and the teacher's status. */
public class TeacherController extends BaseController {

    private final TeacherDao teacherDao;

    public TeacherController(TeacherDao teacherDao, ErrorLogger logger) {
        super(logger);
        this.teacherDao = teacherDao;
    }

    public Result<Teacher> register(Teacher teacher) {
        return execute("Register teacher", () -> {
            if (teacherDao.findByDocumentNumber(teacher.getDocumentNumber()).isPresent()) {
                return Result.fail("Ya existe un profesor con el documento " + teacher.getDocumentNumber() + ".");
            }
            if (teacherDao.findByEmail(teacher.getEmail()).isPresent()) {
                return Result.fail("Ya existe un profesor con el correo " + teacher.getEmail() + ".");
            }
            teacherDao.create(teacher);
            return Result.ok(teacher, "Profesor registrado con código " + teacher.getId() + ".");
        });
    }

    public Result<Teacher> update(Teacher teacher) {
        return execute("Update teacher", () -> {
            boolean emailTaken = teacherDao.findByEmail(teacher.getEmail())
                    .filter(other -> !other.getId().equals(teacher.getId()))
                    .isPresent();
            if (emailTaken) {
                return Result.fail("Ese correo ya pertenece a otro profesor.");
            }
            teacherDao.update(teacher);
            return Result.ok(teacher, "Datos del profesor actualizados.");
        });
    }

    public Result<Teacher> changeStatus(Teacher teacher, TeacherStatus newStatus) {
        return execute("Change teacher status", () -> {
            if (teacher.getStatus() == newStatus) {
                return Result.fail("El profesor ya está en estado " + newStatus.getDisplayName() + ".");
            }
            teacher.setStatus(newStatus);
            teacherDao.update(teacher);
            return Result.ok(teacher, "Estado actualizado a " + newStatus.getDisplayName() + ".");
        });
    }

    /** Used by the form to reject a repeated e-mail before the user types the rest of the data. */
    public Result<Boolean> emailTaken(String email) {
        return execute("Check teacher e-mail", () -> Result.ok(teacherDao.findByEmail(email).isPresent(), ""));
    }

    public Result<List<Teacher>> listAll() {
        return execute("List teachers", () -> Result.ok(teacherDao.findAll(), ""));
    }

    public Result<List<Teacher>> search(String term) {
        return execute("Search teachers", () -> {
            if (term == null || term.isBlank()) {
                throw new ValidationException("Escriba un nombre, apellido, documento o especialización para buscar.");
            }
            return Result.ok(teacherDao.search(term.trim()), "");
        });
    }

    public Result<Teacher> findByDocument(String documentNumber) {
        return execute("Find teacher", () -> teacherDao.findByDocumentNumber(documentNumber)
                .map(teacher -> Result.ok(teacher, ""))
                .orElseGet(() -> Result.fail("No existe un profesor con el documento " + documentNumber + ".")));
    }
}
