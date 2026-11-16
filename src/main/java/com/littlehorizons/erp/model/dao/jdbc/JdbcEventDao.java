package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.EventDao;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.enums.EventPosition;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcEventDao implements EventDao {

    /** Columns of eventos (alias ev) that RowMapper.event needs; reused by the participation DAO. */
    static final String EVENT_COLUMNS = "ev.id_evento, ev.nombre_evento, ev.descripcion, ev.tipo_evento, "
            + "ev.fecha_evento, ev.lugar, ev.tiene_competencia, ev.esta_activo";

    private static final String SELECT = "SELECT " + EVENT_COLUMNS + ", " + JdbcTeacherDao.TEACHER_COLUMNS
            + " FROM eventos ev JOIN profesores p ON p.id_profesor = ev.id_organizador ";
    private static final String ORDER = " ORDER BY ev.fecha_evento, ev.nombre_evento";

    private final DatabaseConnection database;

    JdbcEventDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(SchoolEvent event) throws DaoException {
        String sql = "INSERT INTO eventos (nombre_evento, descripcion, tipo_evento, fecha_evento, lugar, "
                + "tiene_competencia, id_organizador, esta_activo) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, event);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    event.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(SchoolEvent event) throws DaoException {
        Transactions.run(database, connection -> {
            int podium = countPodium(connection, event.getId());
            if (!event.hasCompetition() && podium > 0) {
                throw new DaoException("No se puede quitar la competencia: el evento ya tiene " + podium
                        + " participante(s) con posición en el podio.");
            }
            String sql = "UPDATE eventos SET nombre_evento = ?, descripcion = ?, tipo_evento = ?, fecha_evento = ?, "
                    + "lugar = ?, tiene_competencia = ?, id_organizador = ?, esta_activo = ? WHERE id_evento = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, event);
                statement.setInt(9, event.getId());
                statement.executeUpdate();
            }
            // keep the participants coherent with the competition flag
            String positions = event.hasCompetition()
                    ? "UPDATE participantes_evento SET posicion_obtenida = ? WHERE id_evento = ? AND posicion_obtenida IS NULL"
                    : "UPDATE participantes_evento SET posicion_obtenida = NULL WHERE id_evento = ?";
            try (PreparedStatement statement = connection.prepareStatement(positions)) {
                if (event.hasCompetition()) {
                    statement.setString(1, EventPosition.PARTICIPANT.getDbLabel());
                    statement.setInt(2, event.getId());
                } else {
                    statement.setInt(1, event.getId());
                }
                statement.executeUpdate();
            }
        });
    }

    @Override
    public Optional<SchoolEvent> findById(int id) throws DaoException {
        return query(SELECT + "WHERE ev.id_evento = ?", id).stream().findFirst();
    }

    @Override
    public List<SchoolEvent> findAll() throws DaoException {
        return query(SELECT + ORDER);
    }

    @Override
    public List<SchoolEvent> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query(SELECT + "WHERE LOWER(CONCAT_WS(' ', ev.nombre_evento, ev.descripcion, ev.lugar, p.primer_nombre, "
                + "p.primer_apellido, p.segundo_apellido)) LIKE ?" + ORDER, "%" + escaped + "%");
    }

    @Override
    public boolean nameAndDateExist(String name, LocalDate date, Integer excludeId) throws DaoException {
        String sql = "SELECT 1 FROM eventos WHERE nombre_evento = ? AND fecha_evento = ? AND id_evento <> ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setDate(2, Date.valueOf(date));
            statement.setInt(3, excludeId == null ? 0 : excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public int countParticipants(int eventId) throws DaoException {
        return count("SELECT COUNT(*) FROM participantes_evento WHERE id_evento = ?", eventId);
    }

    @Override
    public Map<Integer, Integer> countParticipantsByEvent() throws DaoException {
        String sql = "SELECT id_evento, COUNT(*) AS participants FROM participantes_evento GROUP BY id_evento";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            Map<Integer, Integer> counts = new HashMap<>();
            while (rs.next()) {
                counts.put(rs.getInt("id_evento"), rs.getInt("participants"));
            }
            return counts;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private static void bind(PreparedStatement statement, SchoolEvent event) throws SQLException {
        statement.setString(1, event.getName());
        statement.setString(2, event.getDescription());
        statement.setString(3, event.getType().getDbLabel());
        statement.setDate(4, Date.valueOf(event.getDate()));
        statement.setString(5, event.getPlace());
        statement.setBoolean(6, event.hasCompetition());
        statement.setInt(7, event.getOrganizer().getId());
        statement.setBoolean(8, event.isActive());
    }

    private static int countPodium(Connection connection, int eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM participantes_evento WHERE id_evento = ? AND posicion_obtenida IN (?,?,?)")) {
            statement.setInt(1, eventId);
            statement.setString(2, EventPosition.FIRST.getDbLabel());
            statement.setString(3, EventPosition.SECOND.getDbLabel());
            statement.setString(4, EventPosition.THIRD.getDbLabel());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int count(String sql, int parameter) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private List<SchoolEvent> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<SchoolEvent> events = new ArrayList<>();
                while (rs.next()) {
                    events.add(RowMapper.event(rs, ""));
                }
                return events;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
