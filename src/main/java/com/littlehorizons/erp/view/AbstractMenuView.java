package com.littlehorizons.erp.view;

import com.littlehorizons.erp.exception.OperationCancelledException;

import java.util.List;

/**
 * TEMPLATE METHOD pattern: the loop "show menu, read option, run it, come back" is identical in
 * every module, so it is written once here. Each subclass only says its title, its options and
 * what each option does. The last option is always "Volver" (go back), and typing "cancelar" in
 * any question aborts the operation and returns to this menu: navigation back from anywhere.
 */
abstract class AbstractMenuView implements MenuView {

    protected final ConsoleInput input;
    protected final ConsoleOutput output;

    protected AbstractMenuView(ConsoleInput input, ConsoleOutput output) {
        this.input = input;
        this.output = output;
    }

    protected abstract String title();

    /** The options without "Volver", which is added automatically as the last one. */
    protected abstract List<String> options();

    /** Runs the chosen option (1-based). */
    protected abstract void handle(int option);

    @Override
    public final void show() {
        List<String> menu = new java.util.ArrayList<>(options());
        menu.add("Volver");
        boolean running = true;
        while (running) {
            output.blank();
            output.title(title());
            output.menu(menu);
            int option;
            try {
                option = input.readInt("Seleccione una opción: ", 1, menu.size());
            } catch (OperationCancelledException e) {
                option = menu.size(); // typing "cancelar" at a menu means "go back"
            }
            if (option == menu.size()) {
                running = false;
            } else {
                try {
                    handle(option);
                } catch (OperationCancelledException e) {
                    output.info("Operación cancelada.");
                }
            }
        }
    }
}
