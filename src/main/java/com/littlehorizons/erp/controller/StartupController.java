package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.ConnectionVerifier;
import com.littlehorizons.erp.util.ErrorLogger;

/**
 * Checks, before the first menu is shown, that the database is reachable. It exists so the
 * view never touches the database layer directly: the view asks, the controller answers with
 * a Result.
 */
public class StartupController {

    private final ConnectionVerifier database;
    private final ErrorLogger logger;

    public StartupController(ConnectionVerifier database, ErrorLogger logger) {
        this.database = database;
        this.logger = logger;
    }

    public Result<Void> checkDatabase() {
        try {
            database.verify();
            return Result.ok("Conexión con la base de datos establecida.");
        } catch (DaoException e) {
            logger.error("Startup check failed: " + e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }
}
