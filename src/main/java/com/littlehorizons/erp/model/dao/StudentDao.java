package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of students and of their links with guardians. The operations that touch several
 * tables (student + guardians, retiring a student) are atomic: either everything is saved or
 * nothing is (JDBC transaction).
 */
public interface StudentDao {

    /** Saves the student and its guardians in one transaction. Guardians without id are inserted. */
    void create(Student student, List<StudentGuardian> guardians) throws DaoException;

    void update(Student student) throws DaoException;

    Optional<Student> findById(int id) throws DaoException;

    Optional<Student> findByDocumentNumber(String documentNumber) throws DaoException;

    List<Student> findAll() throws DaoException;

    /** Case-insensitive search in names, last names and document number. */
    List<Student> search(String term) throws DaoException;

    List<Student> findByGuardian(int guardianId) throws DaoException;

    List<StudentGuardian> findGuardians(int studentId) throws DaoException;

    /** Links one more guardian (inserting it first if it is new). */
    void addGuardian(int studentId, StudentGuardian link) throws DaoException;

    /** Unlinks a guardian. A student must keep at least one, so the DAO refuses to remove the last. */
    void removeGuardian(int studentId, int guardianId) throws DaoException;

    /** Makes one linked guardian the main contact and clears the flag on the others. */
    void setPrincipalGuardian(int studentId, int guardianId) throws DaoException;

    /**
     * Retires the student (logical deletion: the row stays because payments and grades refer to
     * it) and closes its open enrollments and registrations, all in one transaction.
     */
    void retire(int studentId, LocalDate date) throws DaoException;
}
