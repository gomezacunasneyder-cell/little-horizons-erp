package com.littlehorizons.erp.exception;

/**
 * Checked exception thrown by the data-access layer when something fails while talking to the
 * database. Its message is already written for the end user (the original SQL error code is
 * translated by SqlErrorTranslator); the original SQLException travels as the cause so it can
 * be written to error_log.txt with the full stack trace.
 *
 * It is checked on purpose: any controller calling a DAO is forced by the compiler to decide
 * what to do when the database fails.
 */
public class DaoException extends Exception {

    public DaoException(String message) {
        super(message);
    }

    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
