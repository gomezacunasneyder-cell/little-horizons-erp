package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Teacher;

import java.util.List;
import java.util.Optional;

public interface TeacherDao {

    void create(Teacher teacher) throws DaoException;

    /** Updates every editable field, including the status. */
    void update(Teacher teacher) throws DaoException;

    Optional<Teacher> findByDocumentNumber(String documentNumber) throws DaoException;

    Optional<Teacher> findByEmail(String email) throws DaoException;

    List<Teacher> findAll() throws DaoException;

    /** Case-insensitive search in names, last names, document number and specialization. */
    List<Teacher> search(String term) throws DaoException;
}
