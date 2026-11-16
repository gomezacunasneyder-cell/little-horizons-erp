package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Actividades Extracurriculares": the entry to clubs and events. Each option opens its own
 * screen, so this class only routes.
 */
public final class ExtracurricularMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Clubes", "Eventos");

    private final MenuView clubMenu;
    private final MenuView eventMenu;

    public ExtracurricularMenuView(ConsoleInput input, ConsoleOutput output, MenuView clubMenu, MenuView eventMenu) {
        super(input, output);
        this.clubMenu = clubMenu;
        this.eventMenu = eventMenu;
    }

    @Override
    protected String title() {
        return "ACTIVIDADES EXTRACURRICULARES";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> clubMenu.show();
            case 2 -> eventMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
