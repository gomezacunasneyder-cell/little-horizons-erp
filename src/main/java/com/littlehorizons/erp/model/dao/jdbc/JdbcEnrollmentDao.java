package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEnrollmentDao implements EnrollmentDao {

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + "g.id_grado, g.nombre_grado, g.nivel_numerico, "
            + "m.id_matricula, m.anio_academico, m.estado_matricula, m.fecha_matricula, m.fecha_retiro, m.observaciones "
            + "FROM matriculas m "
            + "JOIN estudiantes e ON e.id_estudiante = m.id_estudiante "
            + "JOIN grados g ON g.id_grado = m.id_grado ";

    private final DatabaseConnection database;

    JdbcEnrollmentDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Enrollment enrollment) throws DaoException {
        String sql = "INSERT INTO matriculas (id_estudiante, id_grado, anio_academico, estado_matricula, "
                + "fecha_matricula, observaciones) VALUES (?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, enrollment.getStudent().getId());
            statement.setInt(2, enrollment.getGradeLevel().getId());
            statement.setInt(3, enrollment.getAcademicYear());
            statement.setString(4, enrollment.getStatus().getDbLabel());
            statement.setDate(5, Date.valueOf(enrollment.getEnrollmentDate()));
            statement.setString(6, enrollment.getNotes());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    enrollment.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<Enrollment> findByStudentAndYear(int studentId, int academicYear) throws DaoException {
        return query(SELECT + "WHERE m.id_estudiante = ? AND m.anio_academico = ?", studentId, academicYear)
                .stream().findFirst();
    }

    @Override
    public List<Enrollment> findByStudent(int studentId) throws DaoException {
        return query(SELECT + "WHERE m.id_estudiante = ? ORDER BY m.anio_academico DESC", studentId);
    }

    @Override
    public List<Enrollment> findByGradeAndYear(int gradeId, int academicYear) throws DaoException {
        return query(SELECT + "WHERE m.id_grado = ? AND m.anio_academico = ? "
                + "ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre", gradeId, academicYear);
    }

    @Override
    public void updateStatus(int enrollmentId, EnrollmentStatus status, LocalDate withdrawalDate) throws DaoException {
        String sql = "UPDATE matriculas SET estado_matricula = ?, fecha_retiro = ? WHERE id_matricula = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.getDbLabel());
            statement.setDate(2, withdrawalDate == null ? null : Date.valueOf(withdrawalDate));
            statement.setInt(3, enrollmentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private List<Enrollment> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Enrollment> enrollments = new ArrayList<>();
                while (rs.next()) {
                    enrollments.add(RowMapper.enrollment(rs));
                }
                return enrollments;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
