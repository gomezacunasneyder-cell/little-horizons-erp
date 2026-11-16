package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.StudentConceptPriceDao;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcStudentConceptPriceDao implements StudentConceptPriceDao {

    private static final String SELECT = "SELECT " + JdbcPaymentConceptDao.CONCEPT_COLUMNS
            + ", v.anio_academico, v.monto_ajustado, v.motivo "
            + "FROM valores_concepto_estudiante v JOIN conceptos_pago cp ON cp.id_concepto = v.id_concepto ";

    private final DatabaseConnection database;

    JdbcStudentConceptPriceDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void save(StudentConceptPrice price) throws DaoException {
        // the same two values are bound twice so the statement also runs on MariaDB (VALUES() is deprecated in MySQL 8)
        String sql = "INSERT INTO valores_concepto_estudiante (id_estudiante, id_concepto, anio_academico, monto_ajustado, motivo) "
                + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE monto_ajustado = ?, motivo = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, price.getStudent().getId());
            statement.setInt(2, price.getConcept().getId());
            statement.setInt(3, price.getAcademicYear());
            statement.setBigDecimal(4, price.getAmount());
            statement.setString(5, price.getReason());
            statement.setBigDecimal(6, price.getAmount());
            statement.setString(7, price.getReason());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public boolean delete(Student student, PaymentConcept concept, int academicYear) throws DaoException {
        String sql = "DELETE FROM valores_concepto_estudiante WHERE id_estudiante = ? AND id_concepto = ? AND anio_academico = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, student.getId());
            statement.setInt(2, concept.getId());
            statement.setInt(3, academicYear);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<StudentConceptPrice> find(Student student, PaymentConcept concept, int academicYear) throws DaoException {
        return query(student, SELECT + "WHERE v.id_estudiante = ? AND v.id_concepto = ? AND v.anio_academico = ?",
                student.getId(), concept.getId(), academicYear).stream().findFirst();
    }

    @Override
    public List<StudentConceptPrice> findByStudent(Student student, int academicYear) throws DaoException {
        return query(student, SELECT + "WHERE v.id_estudiante = ? AND v.anio_academico = ? ORDER BY cp.nombre_concepto",
                student.getId(), academicYear);
    }

    private List<StudentConceptPrice> query(Student student, String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<StudentConceptPrice> prices = new ArrayList<>();
                while (rs.next()) {
                    prices.add(RowMapper.studentPrice(rs, student));
                }
                return prices;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
