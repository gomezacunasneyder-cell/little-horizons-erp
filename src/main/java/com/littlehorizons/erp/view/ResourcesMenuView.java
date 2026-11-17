package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión de Recursos": the entry to the resource catalog (phase 4.1) and, later, to the loans.
 * Each option opens its own screen, so this class only routes.
 */
public final class ResourcesMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Catálogo de recursos");

    private final MenuView catalogMenu;

    public ResourcesMenuView(ConsoleInput input, ConsoleOutput output, MenuView catalogMenu) {
        super(input, output);
        this.catalogMenu = catalogMenu;
    }

    @Override
    protected String title() {
        return "GESTIÓN DE RECURSOS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> catalogMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
