package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.PaymentConceptDao;
import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcPaymentConceptDao implements PaymentConceptDao {

    /** Columns that RowMapper.concept needs; the price DAO reuses them in its join. */
    static final String CONCEPT_COLUMNS = "cp.id_concepto, cp.codigo_concepto, cp.nombre_concepto, cp.descripcion, "
            + "cp.monto_base, cp.porcentaje_iva, cp.es_recurrente, cp.esta_activo";

    private static final String SELECT = "SELECT " + CONCEPT_COLUMNS + " FROM conceptos_pago cp ";

    private final DatabaseConnection database;

    JdbcPaymentConceptDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(PaymentConcept concept) throws DaoException {
        String sql = "INSERT INTO conceptos_pago (codigo_concepto, nombre_concepto, descripcion, monto_base, "
                + "porcentaje_iva, es_recurrente, esta_activo) VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, concept);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    concept.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(PaymentConcept concept) throws DaoException {
        String sql = "UPDATE conceptos_pago SET codigo_concepto = ?, nombre_concepto = ?, descripcion = ?, "
                + "monto_base = ?, porcentaje_iva = ?, es_recurrente = ?, esta_activo = ? WHERE id_concepto = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, concept);
            statement.setInt(8, concept.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<PaymentConcept> findById(int id) throws DaoException {
        return query(SELECT + "WHERE cp.id_concepto = ?", id).stream().findFirst();
    }

    @Override
    public List<PaymentConcept> findAll() throws DaoException {
        return query(SELECT + "ORDER BY cp.nombre_concepto");
    }

    @Override
    public List<PaymentConcept> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query(SELECT + "WHERE LOWER(CONCAT_WS(' ', cp.codigo_concepto, cp.nombre_concepto, cp.descripcion)) LIKE ? "
                + "ORDER BY cp.nombre_concepto", "%" + escaped + "%");
    }

    @Override
    public boolean codeExists(String code, Integer excludeId) throws DaoException {
        return exists("SELECT 1 FROM conceptos_pago WHERE codigo_concepto = ? AND id_concepto <> ?", code, excludeId);
    }

    @Override
    public boolean nameExists(String name, Integer excludeId) throws DaoException {
        // the column collation (utf8mb4_unicode_ci) already ignores case and accents
        return exists("SELECT 1 FROM conceptos_pago WHERE nombre_concepto = ? AND id_concepto <> ?", name, excludeId);
    }

    @Override
    public Map<Integer, Integer> countPersonalizedByConcept() throws DaoException {
        String sql = "SELECT id_concepto, COUNT(*) AS students FROM valores_concepto_estudiante GROUP BY id_concepto";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            Map<Integer, Integer> counts = new HashMap<>();
            while (rs.next()) {
                counts.put(rs.getInt("id_concepto"), rs.getInt("students"));
            }
            return counts;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private static void bind(PreparedStatement statement, PaymentConcept concept) throws SQLException {
        statement.setString(1, concept.getCode());
        statement.setString(2, concept.getName());
        statement.setString(3, concept.getDescription());
        statement.setBigDecimal(4, concept.getBaseAmount());
        statement.setInt(5, concept.getVatPercent());
        statement.setBoolean(6, concept.isRecurring());
        statement.setBoolean(7, concept.isActive());
    }

    private boolean exists(String sql, String text, Integer excludeId) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, text);
            statement.setInt(2, excludeId == null ? 0 : excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private List<PaymentConcept> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<PaymentConcept> concepts = new ArrayList<>();
                while (rs.next()) {
                    concepts.add(RowMapper.concept(rs));
                }
                return concepts;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
