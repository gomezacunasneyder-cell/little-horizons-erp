package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.InvoiceDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.InvoiceLine;
import com.littlehorizons.erp.model.entity.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcInvoiceDao implements InvoiceDao {

    private static final String SELECT = "SELECT id_factura, numero_factura, id_estudiante, id_acudiente, "
            + "fecha_emision, cufe, resolucion FROM facturas ";

    private static final String SELECT_LINES = "SELECT d.id_pago, p.numero_recibo, d.descripcion, d.cantidad, "
            + "d.valor_unitario, d.porcentaje_iva, d.valor_iva FROM detalle_factura d "
            + "JOIN pagos p ON p.id_pago = d.id_pago WHERE d.id_factura = ? ORDER BY d.id_detalle";

    private final DatabaseConnection database;

    JdbcInvoiceDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Invoice invoice, Seal seal) throws DaoException {
        int[] id = new int[1];
        try {
            Transactions.run(database, connection -> {
                // the counter is locked first: from here until the commit no other invoice can be saved
                invoice.setSequence(Consecutives.next(connection, Consecutives.INVOICE));
                refuseInvoicedPayments(connection, invoice);
                seal.apply(invoice);
                id[0] = insert(connection, invoice);
            });
        } catch (DaoException | RuntimeException e) {
            // the transaction was rolled back: the number was not used, so the object must not keep it
            invoice.setSequence(0);
            invoice.setCufe(null);
            invoice.setResolution(null);
            throw e;
        }
        invoice.setId(id[0]);
    }

    @Override
    public Optional<Invoice> findByNumber(String number) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + "WHERE numero_factura = ?")) {
            statement.setString(1, Invoice.normalizeNumber(number));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(connection, rs, null, new HashMap<>())) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<Invoice> findByStudent(Student student) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + "WHERE id_estudiante = ? ORDER BY id_factura DESC")) {
            statement.setInt(1, student.getId());
            Map<Integer, Guardian> clients = new HashMap<>();
            List<Invoice> invoices = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    invoices.add(map(connection, rs, student, clients));
                }
            }
            return invoices;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    /** The only place where "a payment is invoiced once" is checked (inside the transaction, after the lock). */
    private static void refuseInvoicedPayments(Connection connection, Invoice invoice) throws SQLException {
        String marks = String.join(",", invoice.getLines().stream().map(line -> "?").toList());
        String sql = "SELECT p.numero_recibo, f.numero_factura FROM detalle_factura d "
                + "JOIN facturas f ON f.id_factura = d.id_factura JOIN pagos p ON p.id_pago = d.id_pago "
                + "WHERE d.id_pago IN (" + marks + ") ORDER BY p.numero_recibo";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (InvoiceLine line : invoice.getLines()) {
                statement.setInt(index++, line.getPaymentId());
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    throw new ValidationException("El pago con recibo " + rs.getString(1) + " ya fue facturado en la factura "
                            + rs.getString(2) + ".");
                }
            }
        }
    }

    private static int insert(Connection connection, Invoice invoice) throws SQLException {
        String sql = "INSERT INTO facturas (numero_factura, id_estudiante, id_acudiente, fecha_emision, subtotal, "
                + "valor_iva, total, cufe, resolucion, nombre_archivo) VALUES (?,?,?,?,?,?,?,?,?,?)";
        int id;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, invoice.getNumber());
            statement.setInt(2, invoice.getStudent().getId());
            statement.setInt(3, invoice.getClient().getId());
            statement.setTimestamp(4, Timestamp.valueOf(invoice.getIssuedAt()));
            statement.setBigDecimal(5, invoice.getSubtotal());
            statement.setBigDecimal(6, invoice.getVatTotal());
            statement.setBigDecimal(7, invoice.getTotal());
            statement.setString(8, invoice.getCufe());
            statement.setString(9, invoice.getResolution());
            statement.setString(10, invoice.getFileName());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        }
        String lineSql = "INSERT INTO detalle_factura (id_factura, id_pago, descripcion, cantidad, valor_unitario, "
                + "porcentaje_iva, subtotal, valor_iva, total) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
            for (InvoiceLine line : invoice.getLines()) {
                statement.setInt(1, id);
                statement.setInt(2, line.getPaymentId());
                statement.setString(3, line.getDescription());
                statement.setInt(4, line.getQuantity());
                statement.setBigDecimal(5, line.getUnitValue());
                statement.setInt(6, line.getVatPercent());
                statement.setBigDecimal(7, line.getSubtotal());
                statement.setBigDecimal(8, line.getVatAmount());
                statement.setBigDecimal(9, line.getTotal());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return id;
    }

    private static Invoice map(Connection connection, ResultSet rs, Student knownStudent, Map<Integer, Guardian> clients)
            throws SQLException {
        int id = rs.getInt("id_factura");
        Student student = knownStudent != null ? knownStudent : loadStudent(connection, rs.getInt("id_estudiante"));
        int clientId = rs.getInt("id_acudiente");
        Guardian client = clients.get(clientId);
        if (client == null) {
            client = loadGuardian(connection, clientId);
            clients.put(clientId, client);
        }
        Invoice invoice = new Invoice(student, client, rs.getTimestamp("fecha_emision").toLocalDateTime(),
                loadLines(connection, id));
        invoice.setId(id);
        invoice.setSequence(Invoice.sequenceOf(rs.getString("numero_factura")));
        invoice.setCufe(rs.getString("cufe"));
        invoice.setResolution(rs.getString("resolucion"));
        return invoice;
    }

    private static List<InvoiceLine> loadLines(Connection connection, int invoiceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_LINES)) {
            statement.setInt(1, invoiceId);
            List<InvoiceLine> lines = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lines.add(new InvoiceLine(rs.getInt("id_pago"), rs.getString("numero_recibo"),
                            rs.getString("descripcion"), rs.getInt("cantidad"), rs.getBigDecimal("valor_unitario"),
                            rs.getInt("porcentaje_iva"), rs.getBigDecimal("valor_iva")));
                }
            }
            return lines;
        }
    }

    private static Student loadStudent(Connection connection, int studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + " FROM estudiantes e WHERE e.id_estudiante = ?")) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return RowMapper.student(rs);
            }
        }
    }

    private static Guardian loadGuardian(Connection connection, int guardianId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                JdbcGuardianDao.SELECT_ALL_COLUMNS + " WHERE id_acudiente = ?")) {
            statement.setInt(1, guardianId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return RowMapper.guardian(rs);
            }
        }
    }
}
