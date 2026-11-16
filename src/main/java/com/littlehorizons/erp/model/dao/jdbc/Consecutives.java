package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Hands out consecutive numbers (receipts and invoices) from the table consecutivos. It
 * must be called INSIDE a transaction: the row is locked with SELECT ... FOR UPDATE until the
 * commit, so two payments at the same time never receive the same number, and if the payment
 * fails the rollback gives the number back (no gaps).
 */
final class Consecutives {

    static final String RECEIPT = "Recibo";
    static final String INVOICE = "Factura";

    private Consecutives() {
    }

    static int next(Connection connection, String type) throws SQLException, DaoException {
        int last;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT ultimo_numero FROM consecutivos WHERE tipo = ? FOR UPDATE")) {
            select.setString(1, type);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new DaoException("Falta el consecutivo «" + type + "» en la tabla consecutivos.");
                }
                last = rs.getInt(1);
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE consecutivos SET ultimo_numero = ? WHERE tipo = ?")) {
            update.setInt(1, last + 1);
            update.setString(2, type);
            update.executeUpdate();
        }
        return last + 1;
    }
}
