package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión Académica": the entry to the academic features. Each option opens its own screen, so
 * this class only routes (grades now; attendance is added in the next phase).
 */
public final class AcademicMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Calificaciones y promedios");

    private final MenuView gradeMenu;

    public AcademicMenuView(ConsoleInput input, ConsoleOutput output, MenuView gradeMenu) {
        super(input, output);
        this.gradeMenu = gradeMenu;
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
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
