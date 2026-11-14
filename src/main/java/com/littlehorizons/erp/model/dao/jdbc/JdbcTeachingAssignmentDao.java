package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.TeachingAssignmentDao;
import com.littlehorizons.erp.model.entity.TeachingAssignment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTeachingAssignmentDao implements TeachingAssignmentDao {

    /** Everything RowMapper.assignment needs; the schedule DAO reuses it for its own joins. */
    static final String ASSIGNMENT_COLUMNS = JdbcTeacherDao.TEACHER_COLUMNS + ", " + JdbcSubjectDao.SUBJECT_COLUMNS
            + ", a.id_asignacion, a.anio_academico, a.seccion";

    static final String ASSIGNMENT_JOINS = " JOIN profesores p ON p.id_profesor = a.id_profesor"
            + " JOIN materias m ON m.id_materia = a.id_materia"
            + " JOIN grados g ON g.id_grado = m.id_grado";

    private static final String SELECT = "SELECT " + ASSIGNMENT_COLUMNS + " FROM asignaciones_profesor a" + ASSIGNMENT_JOINS;

    private final DatabaseConnection database;

    JdbcTeachingAssignmentDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(TeachingAssignment assignment) throws DaoException {
        String sql = "INSERT INTO asignaciones_profesor (id_profesor, id_materia, anio_academico, seccion) VALUES (?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, assignment.getTeacher().getId());
            statement.setInt(2, assignment.getSubject().getId());
            statement.setInt(3, assignment.getAcademicYear());
            statement.setString(4, assignment.getSection());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    assignment.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void delete(int assignmentId) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM asignaciones_profesor WHERE id_asignacion = ?")) {
            statement.setInt(1, assignmentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<TeachingAssignment> findByTeacherAndYear(int teacherId, int academicYear) throws DaoException {
        return query(SELECT + " WHERE a.id_profesor = ? AND a.anio_academico = ? "
                + "ORDER BY g.nivel_numerico, m.nombre_materia, a.seccion", teacherId, academicYear);
    }

    @Override
    public List<TeachingAssignment> findBySubjectAndYear(int subjectId, int academicYear) throws DaoException {
        return query(SELECT + " WHERE a.id_materia = ? AND a.anio_academico = ? ORDER BY a.seccion", subjectId, academicYear);
    }

    @Override
    public Optional<TeachingAssignment> findBySubjectYearAndSection(int subjectId, int academicYear, String section) throws DaoException {
        return query(SELECT + " WHERE a.id_materia = ? AND a.anio_academico = ? AND a.seccion = ?",
                subjectId, academicYear, section).stream().findFirst();
    }

    private List<TeachingAssignment> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<TeachingAssignment> assignments = new ArrayList<>();
                while (rs.next()) {
                    assignments.add(RowMapper.assignment(rs));
                }
                return assignments;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
