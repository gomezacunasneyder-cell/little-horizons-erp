package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.ClubDao;
import com.littlehorizons.erp.model.entity.Club;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcClubDao implements ClubDao {

    /** Columns of clubes (alias c) that RowMapper.club needs; reused by the joins of other DAOs. */
    static final String CLUB_COLUMNS = "c.id_club, c.nombre_club, c.descripcion, c.capacidad_maxima, "
            + "c.horario_reunion, c.esta_activo";

    /** The FROM part shared by every club query: the club with the teacher in charge. */
    static final String CLUB_JOIN = "FROM clubes c JOIN profesores p ON p.id_profesor = c.id_profesor_supervisor ";

    private static final String SELECT = "SELECT " + CLUB_COLUMNS + ", " + JdbcTeacherDao.TEACHER_COLUMNS + " " + CLUB_JOIN;
    private static final String ORDER = " ORDER BY c.nombre_club";

    private final DatabaseConnection database;

    JdbcClubDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Club club) throws DaoException {
        String sql = "INSERT INTO clubes (nombre_club, descripcion, id_profesor_supervisor, capacidad_maxima, "
                + "horario_reunion, esta_activo) VALUES (?,?,?,?,?,?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, club.getName());
            statement.setString(2, club.getDescription());
            statement.setInt(3, club.getSupervisor().getId());
            statement.setInt(4, club.getMaxCapacity());
            statement.setString(5, club.getMeetingSchedule());
            statement.setBoolean(6, club.isActive());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    club.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(Club club) throws DaoException {
        Transactions.run(database, connection -> {
            lockClub(connection, club.getId());
            int active = countActive(connection, club.getId());
            if (club.getMaxCapacity() < active) {
                throw new DaoException("No se puede dejar el cupo en " + club.getMaxCapacity()
                        + ": el club tiene " + active + " miembros activos.");
            }
            String sql = "UPDATE clubes SET nombre_club = ?, descripcion = ?, id_profesor_supervisor = ?, "
                    + "capacidad_maxima = ?, horario_reunion = ?, esta_activo = ? WHERE id_club = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, club.getName());
                statement.setString(2, club.getDescription());
                statement.setInt(3, club.getSupervisor().getId());
                statement.setInt(4, club.getMaxCapacity());
                statement.setString(5, club.getMeetingSchedule());
                statement.setBoolean(6, club.isActive());
                statement.setInt(7, club.getId());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Club> findById(int id) throws DaoException {
        return query(SELECT + "WHERE c.id_club = ?", id).stream().findFirst();
    }

    @Override
    public List<Club> findAll() throws DaoException {
        return query(SELECT + ORDER);
    }

    @Override
    public List<Club> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query(SELECT + "WHERE LOWER(CONCAT_WS(' ', c.nombre_club, c.descripcion, p.primer_nombre, "
                + "p.primer_apellido, p.segundo_apellido)) LIKE ?" + ORDER, "%" + escaped + "%");
    }

    @Override
    public boolean nameExists(String name, Integer excludeId) throws DaoException {
        String sql = "SELECT 1 FROM clubes WHERE nombre_club = ? AND id_club <> ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setInt(2, excludeId == null ? 0 : excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public int countActiveMembers(int clubId) throws DaoException {
        try (Connection connection = database.getConnection()) {
            return countActive(connection, clubId);
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Map<Integer, Integer> countActiveMembersByClub() throws DaoException {
        String sql = "SELECT id_club, COUNT(*) AS members FROM membresias_club "
                + "WHERE estado_membresia = 'Activo' GROUP BY id_club";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            Map<Integer, Integer> counts = new HashMap<>();
            while (rs.next()) {
                counts.put(rs.getInt("id_club"), rs.getInt("members"));
            }
            return counts;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    /** Locks the club row until the transaction ends: other transactions that lock it wait their turn. */
    static void lockClub(Connection connection, int clubId) throws SQLException, DaoException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_club FROM clubes WHERE id_club = ? FOR UPDATE")) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new DaoException("El club ya no existe.");
                }
            }
        }
    }

    static int countActive(Connection connection, int clubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM membresias_club WHERE id_club = ? AND estado_membresia = 'Activo'")) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<Club> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Club> clubs = new ArrayList<>();
                while (rs.next()) {
                    clubs.add(RowMapper.club(rs));
                }
                return clubs;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
