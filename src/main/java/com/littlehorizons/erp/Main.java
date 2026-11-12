package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.StartupController;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.ConsoleInput;
import com.littlehorizons.erp.view.ConsoleOutput;
import com.littlehorizons.erp.view.MainMenuView;

/**
 * Entry point. It only builds the objects the program needs, hands them to the main menu and
 * lets it run. No logic lives here.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ConsoleOutput output = new ConsoleOutput(ConsoleOutput.utf8Stdout());
        ConsoleInput input = ConsoleInput.fromStandardInput(output);
        StartupController startup = new StartupController(DatabaseConnection.getInstance(), ErrorLogger.getInstance());

        new MainMenuView(input, output, startup).start();
    }
}
