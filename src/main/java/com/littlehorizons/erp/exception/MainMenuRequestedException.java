package com.littlehorizons.erp.exception;

/**
 * Thrown by the console input helper when the user types "inicio" at any prompt. It is NOT a
 * subclass of OperationCancelledException on purpose: "cancelar" is caught by the menu that is
 * running (one level back), while this one crosses every menu until the main menu catches it,
 * which is how "go straight to the main menu from anywhere" is implemented in one place.
 */
public class MainMenuRequestedException extends RuntimeException {

    public MainMenuRequestedException() {
        super("The user asked to return to the main menu");
    }
}
