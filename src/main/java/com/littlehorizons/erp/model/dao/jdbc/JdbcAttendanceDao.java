package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.AttendanceDao;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.enums.AttendanceStatus;

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

public class JdbcAttendanceDao implements AttendanceDao {

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + JdbcSubjectDao.SUBJECT_COLUMNS + ", "
            + "a.id_asistencia, a.fecha_asistencia, a.estado_asistencia, a.observaciones, "
            + "COALESCE(a.registrado_por, 0) AS teacher_id, "
            + "COALESCE(TRIM(CONCAT_WS(' ', p.primer_nombre, p.segundo_nombre, p.primer_apellido, p.segundo_apellido)), "
            + "'Sin registrar') AS teacher_name "
            + "FROM asistencias a "
            + "JOIN estudiantes e ON e.id_estudiante = a.id_estudiante "
            + "JOIN materias m ON m.id_materia = a.id_materia "
            + "JOIN grados g ON g.id_grado = m.id_grado "
            + "LEFT JOIN profesores p ON p.id_profesor = a.registrado_por ";

    private static final String ORDER = " ORDER BY a.fecha_asistencia, m.nombre_materia, "
            + "e.primer_apellido, e.primer_nombre";

    private final DatabaseConnection database;

    JdbcAttendanceDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(AttendanceRecord record) throws DaoException {
        String sql = "INSERT INTO asistencias (id_estudiante, id_materia, fecha_asistencia, estado_asistencia, "
                + "observaciones, registrado_por) VALUES (?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, record.getStudent().getId());
            statement.setInt(2, record.getSubject().getId());
            statement.setDate(3, Date.valueOf(record.getDate()));
            statement.setString(4, record.getStatus().getDbLabel());
            statement.setString(5, record.getObservations());
            statement.setInt(6, record.getTeacherId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    record.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(AttendanceRecord record) throws DaoException {
        String sql = "UPDATE asistencias SET estado_asistencia = ?, observaciones = ?, registrado_por = ? "
                + "WHERE id_asistencia = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getStatus().getDbLabel());
            statement.setString(2, record.getObservations());
            statement.setInt(3, record.getTeacherId());
            statement.setInt(4, record.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<AttendanceRecord> find(int studentId, int subjectId, LocalDate date) throws DaoException {
        return query(SELECT + "WHERE a.id_estudiante = ? AND a.id_materia = ? AND a.fecha_asistencia = ?",
                studentId, subjectId, Date.valueOf(date)).stream().findFirst();
    }

    @Override
    public List<AttendanceRecord> findBySubjectAndDate(int subjectId, LocalDate date) throws DaoException {
        return query(SELECT + "WHERE a.id_materia = ? AND a.fecha_asistencia = ?" + ORDER,
                subjectId, Date.valueOf(date));
    }

    @Override
    public List<AttendanceRecord> findByStudentAndDate(int studentId, LocalDate date) throws DaoException {
        return query(SELECT + "WHERE a.id_estudiante = ? AND a.fecha_asistencia = ?" + ORDER,
                studentId, Date.valueOf(date));
    }

    @Override
    public List<AttendanceRecord> findByStudentBetween(int studentId, LocalDate from, LocalDate to) throws DaoException {
        return query(SELECT + "WHERE a.id_estudiante = ? AND a.fecha_asistencia BETWEEN ? AND ?" + ORDER,
                studentId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public List<AttendanceRecord> findByStatusBetween(AttendanceStatus status, LocalDate from, LocalDate to) throws DaoException {
        return query(SELECT + "WHERE a.estado_asistencia = ? AND a.fecha_asistencia BETWEEN ? AND ?" + ORDER,
                status.getDbLabel(), Date.valueOf(from), Date.valueOf(to));
    }

    /** Runs a SELECT whose parameters are ints, strings and dates, in order. */
    private List<AttendanceRecord> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<AttendanceRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(RowMapper.attendanceRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
