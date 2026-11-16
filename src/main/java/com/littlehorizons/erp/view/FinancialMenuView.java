package com.littlehorizons.erp.view;

import java.util.List;

/**
 * "Gestión Financiera": the entry to the payment concepts, the payments and the invoices. Each option opens
 * its own screen, so this class only routes.
 */
public final class FinancialMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of("Conceptos de pago", "Pagos", "Facturas electrónicas");

    private final MenuView conceptMenu;
    private final MenuView paymentMenu;
    private final MenuView invoiceMenu;

    public FinancialMenuView(ConsoleInput input, ConsoleOutput output, MenuView conceptMenu, MenuView paymentMenu,
                             MenuView invoiceMenu) {
        super(input, output);
        this.conceptMenu = conceptMenu;
        this.paymentMenu = paymentMenu;
        this.invoiceMenu = invoiceMenu;
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
            case 3 -> invoiceMenu.show();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }
}
