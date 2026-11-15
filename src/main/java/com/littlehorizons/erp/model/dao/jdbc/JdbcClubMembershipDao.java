package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.ClubMembershipDao;
import com.littlehorizons.erp.model.entity.ClubMembership;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcClubMembershipDao implements ClubMembershipDao {

    /** The teacher in charge is selected with this prefix because the student has columns with the same names. */
    static final String TEACHER_PREFIX = "t_";

    private static final String SELECT = "SELECT " + JdbcStudentDao.STUDENT_COLUMNS + ", "
            + JdbcClubDao.CLUB_COLUMNS + ", " + JdbcTeacherDao.teacherColumnsPrefixed(TEACHER_PREFIX) + ", "
            + "m.id_membresia, m.fecha_ingreso AS fecha_ingreso_club, m.estado_membresia, m.fecha_retiro, m.motivo_retiro "
            + "FROM membresias_club m "
            + "JOIN estudiantes e ON e.id_estudiante = m.id_estudiante "
            + "JOIN clubes c ON c.id_club = m.id_club "
            + "JOIN profesores p ON p.id_profesor = c.id_profesor_supervisor ";

    private final DatabaseConnection database;

    JdbcClubMembershipDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public boolean createIfRoom(ClubMembership membership) throws DaoException {
        boolean[] saved = {false};
        Transactions.run(database, connection -> {
            int clubId = membership.getClub().getId();
            JdbcClubDao.lockClub(connection, clubId);
            if (JdbcClubDao.countActive(connection, clubId) >= capacityOf(connection, clubId)) {
                return; // full: nothing is inserted and the transaction ends without changes
            }
            String sql = "INSERT INTO membresias_club (id_estudiante, id_club, fecha_ingreso, estado_membresia) "
                    + "VALUES (?,?,?,'Activo')";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, membership.getStudent().getId());
                statement.setInt(2, clubId);
                statement.setDate(3, Date.valueOf(membership.getJoinDate()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        membership.setId(keys.getInt(1));
                    }
                }
            }
            saved[0] = true;
        });
        return saved[0];
    }

    @Override
    public boolean saveWithdrawal(ClubMembership membership) throws DaoException {
        String sql = "UPDATE membresias_club SET estado_membresia = 'Retirado', fecha_retiro = ?, motivo_retiro = ? "
                + "WHERE id_membresia = ? AND estado_membresia = 'Activo'";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(membership.getWithdrawalDate()));
            statement.setString(2, membership.getWithdrawalReason());
            statement.setInt(3, membership.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public boolean existsActive(int studentId, int clubId) throws DaoException {
        String sql = "SELECT 1 FROM membresias_club WHERE id_estudiante = ? AND id_club = ? AND estado_membresia = 'Activo'";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public List<ClubMembership> findByClub(int clubId) throws DaoException {
        return query(SELECT + "WHERE m.id_club = ? ORDER BY (m.estado_membresia = 'Activo') DESC, "
                + "e.primer_apellido, e.segundo_apellido, e.primer_nombre, m.fecha_ingreso", clubId);
    }

    @Override
    public List<ClubMembership> findByStudent(int studentId) throws DaoException {
        return query(SELECT + "WHERE m.id_estudiante = ? ORDER BY m.fecha_ingreso DESC, c.nombre_club", studentId);
    }

    private static int capacityOf(Connection connection, int clubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT capacidad_maxima FROM clubes WHERE id_club = ?")) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<ClubMembership> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<ClubMembership> memberships = new ArrayList<>();
                while (rs.next()) {
                    memberships.add(RowMapper.membership(rs));
                }
                return memberships;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
