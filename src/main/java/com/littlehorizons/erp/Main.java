package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.StartupController;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.ConsoleInput;
import com.littlehorizons.erp.view.EnrollmentMenuView;
import com.littlehorizons.erp.view.GuardianMenuView;
import com.littlehorizons.erp.view.MenuView;
import com.littlehorizons.erp.view.StudentMenuView;
import com.littlehorizons.erp.view.SubjectRegistrationMenuView;
import com.littlehorizons.erp.view.ConsoleOutput;
import com.littlehorizons.erp.view.MainMenuView;

import java.util.Map;

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

        // Dependency injection: everything is created here and handed down through constructors.
        ErrorLogger logger = ErrorLogger.getInstance();
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());

        StudentController students = new StudentController(daos.studentDao(), daos.guardianDao(), logger);
        EnrollmentController enrollments = new EnrollmentController(daos.enrollmentDao(), daos.gradeLevelDao(), logger);
        SubjectRegistrationController registrations = new SubjectRegistrationController(
                daos.subjectRegistrationDao(), daos.subjectDao(), daos.enrollmentDao(), logger);

        MenuView studentModule = new StudentMenuView(input, output, students,
                new GuardianMenuView(input, output, students),
                new EnrollmentMenuView(input, output, enrollments, students),
                new SubjectRegistrationMenuView(input, output, registrations, students));

        new MainMenuView(input, output, startup, Map.of(1, studentModule)).start();
    }
}
