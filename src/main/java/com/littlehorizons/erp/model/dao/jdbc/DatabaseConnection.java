package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.util.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The only class that knows how to reach the database. Design pattern: SINGLETON. It holds no
 * connection itself (a shared connection would break try-with-resources and transactions);
 * it is the single, central place that reads the credentials and hands out a NEW Connection
 * every time getConnection() is called.
 *
 * Whoever asks for the connection owns it and must close it with try-with-resources:
 *
 *     try (Connection connection = DatabaseConnection.getInstance().getConnection()) { ... }
 *
 * DriverManager looks at the start of the url ("jdbc:mysql:") and picks the driver that accepts
 * it; since JDBC 4 the driver jar registers itself, so Class.forName(...) is not needed.
 */
public final class DatabaseConnection {

    private static final DatabaseConnection INSTANCE = new DatabaseConnection();

    private DatabaseConnection() {
    }

    public static DatabaseConnection getInstance() {
        return INSTANCE;
    }

    /** Opens a new connection. The caller must close it. */
    public Connection getConnection() throws DaoException {
        String url;
        String user;
        String password;
        try {
            AppConfig config = AppConfig.getInstance();
            url = config.required("db.url");
            user = config.required("db.user");
            password = config.required("db.password");
        } catch (ConfigurationException e) {
            throw new DaoException(e.getMessage(), e);
        }
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    /** Opens and immediately closes a connection, to verify that the database is reachable. */
    public void verify() throws DaoException {
        try (Connection connection = getConnection()) {
            if (!connection.isValid(3)) {
                throw new DaoException("La conexión con MySQL no respondió.");
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
