package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Guardian;

import java.util.Optional;

/** Persistence of guardians. Creating them happens together with a student (see StudentDao). */
public interface GuardianDao {

    Optional<Guardian> findByDocumentNumber(String documentNumber) throws DaoException;

    void update(Guardian guardian) throws DaoException;
}
