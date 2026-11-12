package com.littlehorizons.erp.exception;

/**
 * Unchecked exception thrown when config.txt is missing, unreadable or incomplete.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
