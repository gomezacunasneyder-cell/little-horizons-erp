package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.util.ErrorLogger;

/**
 * Shared behaviour of the controllers: run an operation and convert any expected failure into
 * a Result with a message for the user. The view never sees an exception, and every technical
 * failure is written to error_log.txt exactly once, here (DRY).
 */
abstract class BaseController {

    /** An operation that talks to the DAOs and may fail with DaoException. */
    @FunctionalInterface
    protected interface Operation<T> {
        Result<T> run() throws DaoException;
    }

    private final ErrorLogger logger;

    protected BaseController(ErrorLogger logger) {
        this.logger = logger;
    }

    protected <T> Result<T> execute(String action, Operation<T> operation) {
        try {
            return operation.run();
        } catch (ValidationException e) {
            return Result.fail(e.getMessage());
        } catch (DaoException e) {
            logger.error(action + " failed: " + e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }
}
