package com.littlehorizons.erp.exception;

/**
 * Thrown by the console input helper when the user types "cancelar" in any prompt. Menus catch
 * it and go back one level, which is how "return from any point" is implemented without
 * repeating that check in every screen.
 */
public class OperationCancelledException extends RuntimeException {

    public OperationCancelledException() {
        super("Operation cancelled by the user");
    }

    protected OperationCancelledException(String message) {
        super(message);
    }
}
