package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión Financiera": the entry to the payment concepts and to the payments. Each option opens
 * its own screen, so this class only routes.
 */
public final class FinancialMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Conceptos de pago", "Pagos");

    private final MenuView conceptMenu;
    private final MenuView paymentMenu;

    public FinancialMenuView(ConsoleInput input, ConsoleOutput output, MenuView conceptMenu, MenuView paymentMenu) {
        super(input, output);
        this.conceptMenu = conceptMenu;
        this.paymentMenu = paymentMenu;
    }

    @Override
    protected String title() {
        return "GESTIÓN FINANCIERA";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> conceptMenu.show();
            case 2 -> paymentMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
