package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión de Recursos": the entry to the resource catalog (phase 4.1) and to the loans and
 * returns (phase 4.2). Each option opens its own screen, so this class only routes.
 */
public final class ResourcesMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Catálogo de recursos", "Préstamos y devoluciones");

    private final MenuView catalogMenu;
    private final MenuView loanMenu;

    public ResourcesMenuView(ConsoleInput input, ConsoleOutput output, MenuView catalogMenu, MenuView loanMenu) {
        super(input, output);
        this.catalogMenu = catalogMenu;
        this.loanMenu = loanMenu;
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
            case 2 -> loanMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
