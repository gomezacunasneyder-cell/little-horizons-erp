package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión Académica": the entry to the academic features. Each option opens its own screen, so
 * this class only routes (grades and attendance).
 */
public final class AcademicMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Calificaciones y promedios", "Control de asistencia");

    private final MenuView gradeMenu;
    private final MenuView attendanceMenu;

    public AcademicMenuView(ConsoleInput input, ConsoleOutput output, MenuView gradeMenu, MenuView attendanceMenu) {
        super(input, output);
        this.gradeMenu = gradeMenu;
        this.attendanceMenu = attendanceMenu;
    }

    @Override
    protected String title() {
        return "GESTIÓN ACADÉMICA";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> gradeMenu.show();
            case 2 -> attendanceMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
