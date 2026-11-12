package com.littlehorizons.erp.model.dao.jdbc;

import java.net.ConnectException;
import java.sql.SQLException;

/**
 * Turns a low-level SQLException into a sentence the user can understand. It looks at the
 * MySQL error code (getErrorCode), which identifies the exact failure, instead of parsing
 * the text of the message.
 *
 * Only the DAO layer imports java.sql; controllers and views receive the translated text
 * inside a DaoException.
 */
public final class SqlErrorTranslator {

    private static final int ACCESS_DENIED = 1045;
    private static final int UNKNOWN_DATABASE = 1049;
    private static final int DUPLICATE_ENTRY = 1062;
    private static final int DATA_TOO_LONG = 1406;
    private static final int ROW_IS_REFERENCED = 1451;
    private static final int NO_REFERENCED_ROW = 1452;
    private static final int CHECK_VIOLATED_MYSQL = 3819;   // MySQL 8
    private static final int CHECK_VIOLATED_MARIADB = 4025; // MariaDB (used for local testing)

    private SqlErrorTranslator() {
    }

    public static String toUserMessage(SQLException e) {
        String technical = String.valueOf(e.getMessage());
        if (technical.contains("No suitable driver")) {
            return "No se encontró el driver de MySQL. Verifique que mysql-connector-j esté "
                    + "en el pom.xml y recargue Maven.";
        }
        return switch (e.getErrorCode()) {
            case ACCESS_DENIED -> "Usuario o contraseña de MySQL incorrectos. Revise config.txt.";
            case UNKNOWN_DATABASE -> "La base de datos no existe. Ejecute docs/database.sql primero.";
            case DUPLICATE_ENTRY -> "Ya existe un registro con ese valor único (documento, código o similar).";
            case DATA_TOO_LONG -> "Uno de los datos es demasiado largo para el campo.";
            case ROW_IS_REFERENCED -> "No se puede eliminar: otros registros dependen de este.";
            case NO_REFERENCED_ROW -> "El registro relacionado no existe (por ejemplo, un id inválido).";
            case CHECK_VIOLATED_MYSQL, CHECK_VIOLATED_MARIADB ->
                    "Un valor no cumple las reglas de la base de datos (rango, monto o fecha).";
            default -> isConnectionProblem(e)
                    ? "No se pudo conectar con MySQL. Verifique que el servidor esté en ejecución "
                      + "y que la url de config.txt sea correcta."
                    : "Error inesperado de base de datos. Consulte error_log.txt.";
        };
    }

    private static boolean isConnectionProblem(SQLException e) {
        String state = e.getSQLState();
        boolean connectionState = state != null && state.startsWith("08");
        return connectionState || e.getCause() instanceof ConnectException;
    }
}
