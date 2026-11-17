package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.ResourceDao;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcResourceDao implements ResourceDao {

    /** Columns that RowMapper.resource needs; the loan DAOs of phase 4.2 reuse them in their joins. */
    static final String RESOURCE_COLUMNS = "r.id_recurso, r.codigo_recurso, r.nombre_recurso, c.nombre_categoria, "
            + "r.descripcion, r.fecha_adquisicion, r.ubicacion_fisica, r.estado_recurso";

    private static final String SELECT = "SELECT " + RESOURCE_COLUMNS + " FROM recursos r "
            + "JOIN categorias_recurso c ON c.id_categoria = r.id_categoria ";

    /** The category is stored by id, so it is looked up by its name inside the same statement. */
    private static final String CATEGORY_ID = "(SELECT id_categoria FROM categorias_recurso WHERE nombre_categoria = ?)";

    private final DatabaseConnection database;

    JdbcResourceDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Resource resource) throws DaoException {
        String sql = "INSERT INTO recursos (codigo_recurso, nombre_recurso, id_categoria, descripcion, "
                + "fecha_adquisicion, ubicacion_fisica, estado_recurso) VALUES (?, ?, " + CATEGORY_ID + ", ?, ?, ?, ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindData(statement, resource);
            statement.setString(7, resource.getStatus().getDbLabel());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    resource.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(Resource resource) throws DaoException {
        String sql = "UPDATE recursos SET codigo_recurso = ?, nombre_recurso = ?, id_categoria = " + CATEGORY_ID
                + ", descripcion = ?, fecha_adquisicion = ?, ubicacion_fisica = ? WHERE id_recurso = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindData(statement, resource);
            statement.setInt(7, resource.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<Resource> findById(int id) throws DaoException {
        return query(SELECT + "WHERE r.id_recurso = ?", id).stream().findFirst();
    }

    @Override
    public List<Resource> findAll() throws DaoException {
        return query(SELECT + "ORDER BY r.codigo_recurso");
    }

    @Override
    public List<Resource> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query(SELECT + "WHERE LOWER(CONCAT_WS(' ', r.codigo_recurso, r.nombre_recurso, r.descripcion, "
                + "r.ubicacion_fisica, c.nombre_categoria)) LIKE ? ORDER BY r.codigo_recurso", "%" + escaped + "%");
    }

    @Override
    public boolean codeExists(String code, Integer excludeId) throws DaoException {
        String sql = "SELECT 1 FROM recursos WHERE codigo_recurso = ? AND id_recurso <> ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setInt(2, excludeId == null ? 0 : excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public boolean changeStatus(int id, ResourceStatus from, ResourceStatus to) throws DaoException {
        String sql = "UPDATE recursos SET estado_recurso = ? WHERE id_recurso = ? AND estado_recurso = ?";
        return executeUpdate(sql, to.getDbLabel(), id, from.getDbLabel());
    }

    @Override
    public boolean deleteIfNeverBorrowed(int id) throws DaoException {
        String sql = "DELETE FROM recursos WHERE id_recurso = ? AND estado_recurso <> ? "
                + "AND NOT EXISTS (SELECT 1 FROM prestamos_estudiante pe WHERE pe.id_recurso = recursos.id_recurso) "
                + "AND NOT EXISTS (SELECT 1 FROM prestamos_profesor pp WHERE pp.id_recurso = recursos.id_recurso)";
        return executeUpdate(sql, id, ResourceStatus.BORROWED.getDbLabel());
    }

    /** Parameters 1 to 6, in the order shared by the INSERT and the UPDATE. */
    private static void bindData(PreparedStatement statement, Resource resource) throws SQLException {
        statement.setString(1, resource.getCode());
        statement.setString(2, resource.getName());
        statement.setString(3, resource.getCategory().getDbLabel());
        statement.setString(4, resource.getDescription());
        statement.setDate(5, resource.getAcquisitionDate() == null ? null : Date.valueOf(resource.getAcquisitionDate()));
        statement.setString(6, resource.getLocation());
    }

    private boolean executeUpdate(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    private List<Resource> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Resource> resources = new ArrayList<>();
                while (rs.next()) {
                    resources.add(RowMapper.resource(rs));
                }
                return resources;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
