package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.SubjectDao;
import com.littlehorizons.erp.model.entity.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcSubjectDao implements SubjectDao {

    static final String SUBJECT_COLUMNS = "m.id_materia, m.codigo_materia, m.nombre_materia, m.horas_semanales, "
            + "m.esta_activa, g.id_grado, g.nombre_grado, g.nivel_numerico";

    private static final String SELECT = "SELECT " + SUBJECT_COLUMNS
            + " FROM materias m JOIN grados g ON g.id_grado = m.id_grado ";

    private final DatabaseConnection database;

    JdbcSubjectDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public List<Subject> findActiveByGrade(int gradeId) throws DaoException {
        return query(SELECT + "WHERE m.id_grado = ? AND m.esta_activa = TRUE ORDER BY m.nombre_materia", gradeId);
    }

    @Override
    public Optional<Subject> findById(int id) throws DaoException {
        return query(SELECT + "WHERE m.id_materia = ?", id).stream().findFirst();
    }

    private List<Subject> query(String sql, int parameter) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                List<Subject> subjects = new ArrayList<>();
                while (rs.next()) {
                    subjects.add(RowMapper.subject(rs));
                }
                return subjects;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
