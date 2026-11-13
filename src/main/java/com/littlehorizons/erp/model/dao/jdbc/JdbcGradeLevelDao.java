package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.GradeLevelDao;
import com.littlehorizons.erp.model.entity.GradeLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcGradeLevelDao implements GradeLevelDao {

    private static final String SELECT = "SELECT id_grado, nombre_grado, nivel_numerico FROM grados";

    private final DatabaseConnection database;

    JdbcGradeLevelDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public List<GradeLevel> findAll() throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " ORDER BY nivel_numerico");
             ResultSet rs = statement.executeQuery()) {
            List<GradeLevel> grades = new ArrayList<>();
            while (rs.next()) {
                grades.add(RowMapper.gradeLevel(rs));
            }
            return grades;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<GradeLevel> findById(int id) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE id_grado = ?")) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(RowMapper.gradeLevel(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
