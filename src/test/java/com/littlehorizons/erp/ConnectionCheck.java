package com.littlehorizons.erp;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.SqlErrorTranslator;
import com.littlehorizons.erp.util.AppConfig;
import com.littlehorizons.erp.util.ErrorLogger;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manual check of Phase 0 (plain Java, no framework). Run its main() from IntelliJ with the
 * database loaded and config.txt in place. Every check prints PASS or FAIL; the process exit
 * code is 1 if any check fails.
 */
public final class ConnectionCheck {w

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        check("config.txt is read and has db.url", () -> !AppConfig.getInstance().required("db.url").isEmpty());

        check("a connection opens and answers SELECT 1", () -> {
            try (Connection connection = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        });

        check("schema has the expected 24 tables", () -> {
            String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()";
            try (Connection connection = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 24;
            }
        });

        check("catalog data is loaded (12 grade levels, 7 payment concepts)", () -> count("grados") == 12 && count("conceptos_pago") == 7);

        check("duplicate key is translated (1062)", () -> translated(
                "INSERT INTO grados (nombre_grado, nivel_numerico) VALUES ('Once', 99)",
                "valor único"));

        check("CHECK violation is translated", () -> translated(
                "INSERT INTO conceptos_pago (codigo_concepto, nombre_concepto, monto_base) VALUES ('X', 'X', 0)",
                "reglas de la base de datos"));

        check("foreign key violation is translated (1452)", () -> translated(
                "INSERT INTO materias (codigo_materia, nombre_materia, id_grado) VALUES ('Z-1', 'Z', 9999)",
                "no existe"));

        check("ErrorLogger appends a line with a stack trace", () -> {
            ErrorLogger.getInstance().error("ConnectionCheck sample error", new IllegalStateException("sample"));
            String content = Files.readString(ErrorLogger.getInstance().getLogFile());
            return content.contains("[ERROR] ConnectionCheck sample error") && content.contains("IllegalStateException");
        });

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static int count(String table) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Runs a statement that must fail and checks that the translated message contains the text. */
    private static boolean translated(String sql, String expectedText) throws DaoException, SQLException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
            return false; // it should have failed
        } catch (SQLException e) {
            String message = SqlErrorTranslator.toUserMessage(e);
            System.out.println("      message: " + message);
            return message.contains(expectedText);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean run() throws Exception;
    }

    private static void check(String name, Check check) {
        try {
            boolean ok = check.run();
            System.out.println((ok ? "PASS  " : "FAIL  ") + name);
            if (!ok) {
                failures++;
            }
        } catch (Exception e) {
            System.out.println("FAIL  " + name + "  -> " + e);
            failures++;
        }
    }
}
