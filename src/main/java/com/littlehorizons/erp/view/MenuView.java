package com.littlehorizons.erp.view;

/** A screen with a menu. The main menu opens one MenuView per module. */
public interface MenuView {

    /** Shows the menu and keeps running until the user chooses to go back. */
    void show();
}
