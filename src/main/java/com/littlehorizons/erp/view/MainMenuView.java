package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StartupController;
import com.littlehorizons.erp.exception.InputClosedException;
import com.littlehorizons.erp.exception.OperationCancelledException;

import java.util.List;

/**
 * Main menu: groups the options by module and sends the user to each one. It contains no
 * business logic; each module will have its own view class that this menu opens.
 */
public final class MainMenuView {

    private static final int EXIT_OPTION = 8;
    private static final List<String> OPTIONS = List.of(
            "Gestión de Estudiantes",
            "Gestión Académica",
            "Gestión de Profesores",
            "Actividades Extracurriculares",
            "Gestión Financiera",
            "Gestión de Recursos",
            "Reportes y Estadísticas",
            "Salir");

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final StartupController startup;

    public MainMenuView(ConsoleInput input, ConsoleOutput output, StartupController startup) {
        this.input = input;
        this.output = output;
        this.startup = startup;
    }

    public void start() {
        output.banner("ERP Colegio Little Horizons", "Sistema de Gestión Educativa");

        Result<Void> database = startup.checkDatabase();
        if (!database.isSuccess()) {
            output.error(database.getMessage());
            output.info("Corrija el problema y vuelva a ejecutar. Detalle técnico en error_log.txt.");
            return;
        }
        output.success(database.getMessage());

        boolean running = true;
        while (running) {
            output.blank();
            output.menu(OPTIONS);
            try {
                int option = input.readInt("Seleccione una opción: ", 1, OPTIONS.size());
                running = handle(option);
            } catch (InputClosedException e) {
                running = false;
            } catch (OperationCancelledException e) {
                output.info("Use la opción " + EXIT_OPTION + " para salir.");
            }
        }
    }

    /** Runs the chosen option. Returns false when the user wants to leave the application. */
    private boolean handle(int option) {
        if (option == EXIT_OPTION) {
            output.info("Hasta pronto.");
            return false;
        }
        output.info("«" + OPTIONS.get(option - 1) + "» estará disponible en una próxima fase.");
        return true;
    }
}
