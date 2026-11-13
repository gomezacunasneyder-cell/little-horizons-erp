package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.GuardianDao;
import com.littlehorizons.erp.model.entity.Guardian;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcGuardianDao implements GuardianDao {

    static final String SELECT_ALL_COLUMNS = "SELECT id_acudiente, tipo_documento, numero_documento, primer_nombre, "
            + "segundo_nombre, primer_apellido, segundo_apellido, parentesco, telefono_principal, "
            + "telefono_secundario, correo_electronico, direccion, ocupacion, es_responsable_financiero "
            + "FROM acudientes";

    private static final String UPDATE = "UPDATE acudientes SET parentesco = ?, telefono_principal = ?, "
            + "telefono_secundario = ?, correo_electronico = ?, direccion = ?, ocupacion = ?, "
            + "es_responsable_financiero = ? WHERE id_acudiente = ?";

    private final DatabaseConnection database;

    JdbcGuardianDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public Optional<Guardian> findByDocumentNumber(String documentNumber) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_COLUMNS + " WHERE numero_documento = ?")) {
            statement.setString(1, documentNumber);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(RowMapper.guardian(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(Guardian guardian) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, guardian.getRelationship());
            statement.setString(2, guardian.getPhone());
            statement.setString(3, guardian.getSecondaryPhone());
            statement.setString(4, guardian.getEmail());
            statement.setString(5, guardian.getAddress());
            statement.setString(6, guardian.getOccupation());
            statement.setBoolean(7, guardian.isFinanciallyResponsible());
            statement.setInt(8, guardian.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
