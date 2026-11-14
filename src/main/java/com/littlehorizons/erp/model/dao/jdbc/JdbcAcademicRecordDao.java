package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.AcademicRecordDao;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAcademicRecordDao implements AcademicRecordDao {

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + JdbcSubjectDao.SUBJECT_COLUMNS + ", "
            + "r.id_registro, r.anio_academico, r.periodo_academico, r.valor_nota, r.observaciones, "
            + "r.fecha_registro, r.id_profesor AS teacher_id, "
            + "TRIM(CONCAT_WS(' ', p.primer_nombre, p.segundo_nombre, p.primer_apellido, p.segundo_apellido)) AS teacher_name "
            + "FROM registros_academicos r "
            + "JOIN estudiantes e ON e.id_estudiante = r.id_estudiante "
            + "JOIN materias m ON m.id_materia = r.id_materia "
            + "JOIN grados g ON g.id_grado = m.id_grado "
            + "JOIN profesores p ON p.id_profesor = r.id_profesor ";

    private static final String ORDER = " ORDER BY r.anio_academico, m.nombre_materia, "
            + "FIELD(r.periodo_academico, 'I','II','III','IV','Final')";

    private final DatabaseConnection database;

    JdbcAcademicRecordDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(AcademicRecord record) throws DaoException {
        String sql = "INSERT INTO registros_academicos (id_estudiante, id_materia, id_profesor, anio_academico, "
                + "periodo_academico, valor_nota, observaciones, fecha_registro) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, record.getStudent().getId());
            statement.setInt(2, record.getSubject().getId());
            statement.setInt(3, record.getTeacherId());
            statement.setInt(4, record.getAcademicYear());
            statement.setString(5, record.getPeriod().getDbLabel());
            statement.setBigDecimal(6, record.getGrade());
            statement.setString(7, record.getObservations());
            statement.setDate(8, Date.valueOf(record.getRecordDate()));
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
    public void update(AcademicRecord record) throws DaoException {
        String sql = "UPDATE registros_academicos SET valor_nota = ?, observaciones = ?, id_profesor = ?, "
                + "fecha_registro = ? WHERE id_registro = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, record.getGrade());
            statement.setString(2, record.getObservations());
            statement.setInt(3, record.getTeacherId());
            statement.setDate(4, Date.valueOf(record.getRecordDate()));
            statement.setInt(5, record.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<AcademicRecord> find(int studentId, int subjectId, int academicYear, AcademicPeriod period) throws DaoException {
        List<AcademicRecord> found = query(SELECT + "WHERE r.id_estudiante = ? AND r.id_materia = ? "
                + "AND r.anio_academico = ? AND r.periodo_academico = ?",
                studentId, subjectId, academicYear, period.getDbLabel());
        return found.stream().findFirst();
    }

    @Override
    public List<AcademicRecord> findByStudent(int studentId) throws DaoException {
        return query(SELECT + "WHERE r.id_estudiante = ?" + ORDER, studentId);
    }

    @Override
    public List<AcademicRecord> findByStudentAndYear(int studentId, int academicYear) throws DaoException {
        return query(SELECT + "WHERE r.id_estudiante = ? AND r.anio_academico = ?" + ORDER, studentId, academicYear);
    }

    @Override
    public List<AcademicRecord> findByGradeAndYear(int gradeId, int academicYear) throws DaoException {
        return query(SELECT + "WHERE m.id_grado = ? AND r.anio_academico = ?" + ORDER, gradeId, academicYear);
    }

    @Override
    public List<AcademicRecord> findBySubjectYearAndPeriod(int subjectId, int academicYear, AcademicPeriod period) throws DaoException {
        return query(SELECT + "WHERE r.id_materia = ? AND r.anio_academico = ? AND r.periodo_academico = ?",
                subjectId, academicYear, period.getDbLabel());
    }

    /** Runs a SELECT whose only parameters are ints and strings, in order. */
    private List<AcademicRecord> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<AcademicRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(RowMapper.academicRecord(rs));
                }
                return records;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
