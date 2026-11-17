package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;

/**
 * The one thing the start-up check needs from the data layer: "can you reach the database?".
 * The controller depends on this interface (Dependency Inversion) and not on the JDBC class that
 * implements it, so the controller layer never imports anything from model.dao.jdbc.
 */
@FunctionalInterface
public interface ConnectionVerifier {

    /** Returns normally when the database answers; throws DaoException (with a user message) when it does not. */
    void verify() throws DaoException;
}
