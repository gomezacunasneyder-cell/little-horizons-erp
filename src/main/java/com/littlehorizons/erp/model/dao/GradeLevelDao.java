package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.GradeLevel;

import java.util.List;
import java.util.Optional;

/** Read access to the grade catalog (the 12 grades are fixed data, not edited by the user). */
public interface GradeLevelDao {

    List<GradeLevel> findAll() throws DaoException;

    Optional<GradeLevel> findById(int id) throws DaoException;
}
