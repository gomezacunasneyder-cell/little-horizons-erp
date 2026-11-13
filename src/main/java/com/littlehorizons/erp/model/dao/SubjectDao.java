package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Subject;

import java.util.List;
import java.util.Optional;

public interface SubjectDao {

    /** Active subjects of one grade, ordered by name. */
    List<Subject> findActiveByGrade(int gradeId) throws DaoException;

    Optional<Subject> findById(int id) throws DaoException;
}
