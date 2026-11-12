package com.littlehorizons.erp.exception;

/**
 * Unchecked exception for data that breaks a business or format rule (empty name, score out of
 * range, club without free seats...). The message is meant to be shown to the user as is.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
