package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.SubjectRegistrationDao;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcSubjectRegistrationDao implements SubjectRegistrationDao {

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + JdbcSubjectDao.SUBJECT_COLUMNS + ", "
            + "i.id_inscripcion, i.anio_academico, i.periodo_academico, i.seccion, i.fecha_inscripcion, "
            + "i.estado AS registration_status "
            + "FROM inscripciones_materia i "
            + "JOIN estudiantes e ON e.id_estudiante = i.id_estudiante "
            + "JOIN materias m ON m.id_materia = i.id_materia "
            + "JOIN grados g ON g.id_grado = m.id_grado ";

    private final DatabaseConnection database;

    JdbcSubjectRegistrationDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(SubjectRegistration registration) throws DaoException {
        String sql = "INSERT INTO inscripciones_materia (id_estudiante, id_materia, anio_academico, "
                + "periodo_academico, seccion, fecha_inscripcion, estado) VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, registration.getStudent().getId());
            statement.setInt(2, registration.getSubject().getId());
            statement.setInt(3, registration.getAcademicYear());
            statement.setString(4, registration.getPeriod().getDbLabel());
            statement.setString(5, registration.getSection());
            statement.setDate(6, Date.valueOf(registration.getRegistrationDate()));
            statement.setString(7, registration.getStatus().getDbLabel());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    registration.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public boolean exists(int studentId, int subjectId, int academicYear, AcademicPeriod period) throws DaoException {
        String sql = "SELECT 1 FROM inscripciones_materia WHERE id_estudiante = ? AND id_materia = ? "
                + "AND anio_academico = ? AND periodo_academico = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            statement.setInt(3, academicYear);
            statement.setString(4, period.getDbLabel());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<SubjectRegistration> findByStudentAndYear(int studentId, int academicYear) throws DaoException {
        String sql = SELECT + "WHERE i.id_estudiante = ? AND i.anio_academico = ? "
                + "ORDER BY FIELD(i.periodo_academico, 'I','II','III','IV'), m.nombre_materia";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, academicYear);
            try (ResultSet rs = statement.executeQuery()) {
                List<SubjectRegistration> registrations = new ArrayList<>();
                while (rs.next()) {
                    registrations.add(RowMapper.registration(rs));
                }
                return registrations;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void updateStatus(int registrationId, RegistrationStatus status) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE inscripciones_materia SET estado = ? WHERE id_inscripcion = ?")) {
            statement.setString(1, status.getDbLabel());
            statement.setInt(2, registrationId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
