package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.EventParticipationDao;
import com.littlehorizons.erp.model.entity.EventParticipation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcEventParticipationDao implements EventParticipationDao {

    /** The organizer is selected with this prefix because the student has columns with the same names (estado, telefono...). */
    static final String TEACHER_PREFIX = "t_";

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + JdbcEventDao.EVENT_COLUMNS + ", " + JdbcTeacherDao.teacherColumnsPrefixed(TEACHER_PREFIX) + ", "
            + "pe.id_participacion, pe.posicion_obtenida "
            + "FROM participantes_evento pe "
            + "JOIN estudiantes e ON e.id_estudiante = pe.id_estudiante "
            + "JOIN eventos ev ON ev.id_evento = pe.id_evento "
            + "JOIN profesores p ON p.id_profesor = ev.id_organizador ";

    private final DatabaseConnection database;

    JdbcEventParticipationDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(EventParticipation participation) throws DaoException {
        String sql = "INSERT INTO participantes_evento (id_evento, id_estudiante, posicion_obtenida) VALUES (?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, participation.getEvent().getId());
            statement.setInt(2, participation.getStudent().getId());
            statement.setString(3, positionLabel(participation));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    participation.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void updatePosition(EventParticipation participation) throws DaoException {
        execute("UPDATE participantes_evento SET posicion_obtenida = ? WHERE id_participacion = ?",
                positionLabel(participation), participation.getId());
    }

    @Override
    public void delete(EventParticipation participation) throws DaoException {
        execute("DELETE FROM participantes_evento WHERE id_participacion = ?", participation.getId());
    }

    @Override
    public boolean exists(int eventId, int studentId) throws DaoException {
        String sql = "SELECT 1 FROM participantes_evento WHERE id_evento = ? AND id_estudiante = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            statement.setInt(2, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<EventParticipation> findByEvent(int eventId) throws DaoException {
        return query(SELECT + "WHERE pe.id_evento = ? ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre", eventId);
    }

    @Override
    public List<EventParticipation> findByStudent(int studentId) throws DaoException {
        return query(SELECT + "WHERE pe.id_estudiante = ? ORDER BY ev.fecha_evento DESC, ev.nombre_evento", studentId);
    }

    private static String positionLabel(EventParticipation participation) {
        return participation.getPosition() == null ? null : participation.getPosition().getDbLabel();
    }

    private void execute(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private List<EventParticipation> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<EventParticipation> participations = new ArrayList<>();
                while (rs.next()) {
                    participations.add(RowMapper.participation(rs));
                }
                return participations;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
