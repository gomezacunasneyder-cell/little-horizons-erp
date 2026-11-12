package com.littlehorizons.erp.exception;

/**
 * Thrown when standard input ends (Ctrl+D / Ctrl+Z or a closed pipe). It is a special case of
 * a cancelled operation, but the main menu uses it to exit instead of asking again forever.
 */
public class InputClosedException extends OperationCancelledException {

    public InputClosedException() {
        super("Standard input was closed");
    }
}
