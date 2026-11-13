package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a block of SQL work as ONE transaction: all statements are saved together (commit) or,
 * if anything fails, none is (rollback). The work is received as a lambda, so every DAO
 * method that needs a transaction writes only its SQL and not the commit/rollback boilerplate.
 */
final class Transactions {

    /** A block of work that uses the connection of the transaction. */
    @FunctionalInterface
    interface SqlWork {
        void execute(Connection connection) throws SQLException, DaoException;
    }

    private Transactions() {
    }

    static void run(DatabaseConnection database, SqlWork work) throws DaoException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.execute(connection);
                connection.commit();
            } catch (SQLException | DaoException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original error is the one worth reporting; closing the connection undoes the work anyway
        }
    }
}
