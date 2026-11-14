package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.ScheduleDao;
import com.littlehorizons.erp.model.entity.ScheduleSlot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class JdbcScheduleDao implements ScheduleDao {

    private static final String SELECT = "SELECT " + JdbcTeachingAssignmentDao.ASSIGNMENT_COLUMNS
            + ", h.id_horario, h.dia_semana, h.hora_inicio, h.hora_fin "
            + "FROM horarios_asignacion h JOIN asignaciones_profesor a ON a.id_asignacion = h.id_asignacion"
            + JdbcTeachingAssignmentDao.ASSIGNMENT_JOINS + " ";

    private static final String ORDER = " ORDER BY FIELD(h.dia_semana, 'Lunes','Martes','Miércoles','Jueves','Viernes','Sábado'), h.hora_inicio";

    private final DatabaseConnection database;

    JdbcScheduleDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(ScheduleSlot slot) throws DaoException {
        String sql = "INSERT INTO horarios_asignacion (id_asignacion, dia_semana, hora_inicio, hora_fin) VALUES (?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, slot.getAssignment().getId());
            statement.setString(2, slot.getDay().getDbLabel());
            statement.setTime(3, Time.valueOf(slot.getStart()));
            statement.setTime(4, Time.valueOf(slot.getEnd()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    slot.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void delete(int slotId) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM horarios_asignacion WHERE id_horario = ?")) {
            statement.setInt(1, slotId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<ScheduleSlot> findByAssignment(int assignmentId) throws DaoException {
        return query(SELECT + "WHERE h.id_asignacion = ?" + ORDER, assignmentId);
    }

    @Override
    public List<ScheduleSlot> findByTeacherAndYear(int teacherId, int academicYear) throws DaoException {
        return query(SELECT + "WHERE a.id_profesor = ? AND a.anio_academico = ?" + ORDER, teacherId, academicYear);
    }

    @Override
    public List<ScheduleSlot> findByGroup(int gradeId, String section, int academicYear) throws DaoException {
        return query(SELECT + "WHERE m.id_grado = ? AND a.seccion = ? AND a.anio_academico = ?" + ORDER,
                gradeId, section, academicYear);
    }

    private List<ScheduleSlot> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<ScheduleSlot> slots = new ArrayList<>();
                while (rs.next()) {
                    slots.add(RowMapper.slot(rs));
                }
                return slots;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
