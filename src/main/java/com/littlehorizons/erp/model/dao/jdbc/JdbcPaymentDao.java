package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.PaidInstallment;
import com.littlehorizons.erp.model.dao.PaymentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcPaymentDao implements PaymentDao {

    private static final String SELECT = "SELECT " + JdbcPaymentConceptDao.CONCEPT_COLUMNS + ", "
            + "p.id_pago, p.id_acudiente, p.monto_pagado, p.fecha_pago, p.metodo_pago, p.numero_recibo, "
            + "p.anio_academico, p.mes_cuota "
            + "FROM pagos p JOIN conceptos_pago cp ON cp.id_concepto = p.id_concepto ";

    private static final String INSTALLMENT_TOTAL = "SELECT COALESCE(SUM(monto_pagado), 0) FROM pagos "
            + "WHERE id_estudiante = ? AND id_concepto = ? AND anio_academico = ? AND mes_cuota <=> ?";

    private final DatabaseConnection database;

    JdbcPaymentDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Payment payment, BigDecimal owed) throws DaoException {
        String[] receipt = new String[1];
        int[] id = new int[1];
        Transactions.run(database, connection -> {
            // the receipt counter is locked first: from here until the commit no other payment can be saved
            int number = Consecutives.next(connection, Consecutives.RECEIPT);
            BigDecimal alreadyPaid = installmentTotal(connection, payment.getStudent().getId(),
                    payment.getConcept().getId(), payment.getAcademicYear(), payment.getInstallmentMonth());
            if (alreadyPaid.add(payment.getAmount()).compareTo(owed) > 0) {
                throw new ValidationException("Por este concepto el estudiante debe " + Money.format(owed)
                        + " y ya tiene pagados " + Money.format(alreadyPaid) + ": el pago de "
                        + Money.format(payment.getAmount()) + " lo superaría (pendiente: "
                        + Money.format(owed.subtract(alreadyPaid).max(BigDecimal.ZERO)) + ").");
            }
            receipt[0] = String.format("REC-%06d", number);
            String sql = "INSERT INTO pagos (id_estudiante, id_acudiente, id_concepto, monto_pagado, fecha_pago, "
                    + "metodo_pago, numero_recibo, anio_academico, mes_cuota) VALUES (?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, payment.getStudent().getId());
                if (payment.getPayer() == null) {
                    statement.setNull(2, Types.INTEGER);
                } else {
                    statement.setInt(2, payment.getPayer().getId());
                }
                statement.setInt(3, payment.getConcept().getId());
                statement.setBigDecimal(4, payment.getAmount());
                statement.setDate(5, Date.valueOf(payment.getPaymentDate()));
                statement.setString(6, payment.getMethod().getDbLabel());
                statement.setString(7, receipt[0]);
                statement.setInt(8, payment.getAcademicYear());
                if (payment.getInstallmentMonth() == null) {
                    statement.setNull(9, Types.TINYINT);
                } else {
                    statement.setInt(9, payment.getInstallmentMonth());
                }
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id[0] = keys.getInt(1);
                    }
                }
            }
        });
        // only after the commit does the object receive its id and its receipt number
        payment.setId(id[0]);
        payment.setReceiptNumber(receipt[0]);
    }

    @Override
    public List<Payment> findByStudent(Student student) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + "WHERE p.id_estudiante = ? ORDER BY p.fecha_pago DESC, p.id_pago DESC")) {
            statement.setInt(1, student.getId());
            Map<Integer, Guardian> payers = new HashMap<>();
            List<Payment> payments = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int payerId = rs.getInt("id_acudiente");
                    Guardian payer = rs.wasNull() ? null : payers.get(payerId);
                    if (payer == null && payerId != 0) {
                        payer = loadGuardian(connection, payerId);
                        payers.put(payerId, payer);
                    }
                    payments.add(RowMapper.payment(rs, student, payer));
                }
            }
            return payments;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public BigDecimal paidFor(int studentId, int conceptId, int academicYear, Integer month) throws DaoException {
        try (Connection connection = database.getConnection()) {
            return installmentTotal(connection, studentId, conceptId, academicYear, month);
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<PaidInstallment> findInstallments(int conceptId, int academicYear) throws DaoException {
        String sql = "SELECT id_estudiante, mes_cuota, SUM(monto_pagado) AS total FROM pagos "
                + "WHERE id_concepto = ? AND anio_academico = ? AND mes_cuota IS NOT NULL "
                + "GROUP BY id_estudiante, mes_cuota";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, conceptId);
            statement.setInt(2, academicYear);
            try (ResultSet rs = statement.executeQuery()) {
                List<PaidInstallment> paid = new ArrayList<>();
                while (rs.next()) {
                    paid.add(new PaidInstallment(rs.getInt("id_estudiante"), rs.getInt("mes_cuota"), rs.getBigDecimal("total")));
                }
                return paid;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private static BigDecimal installmentTotal(Connection connection, int studentId, int conceptId, int year, Integer month)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSTALLMENT_TOTAL)) {
            statement.setInt(1, studentId);
            statement.setInt(2, conceptId);
            statement.setInt(3, year);
            if (month == null) {
                statement.setNull(4, Types.TINYINT);
            } else {
                statement.setInt(4, month);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static Guardian loadGuardian(Connection connection, int guardianId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcGuardianDao.SELECT_ALL_COLUMNS + " WHERE id_acudiente = ?")) {
            statement.setInt(1, guardianId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? RowMapper.guardian(rs) : null;
            }
        }
    }
}
