package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.TeacherDao;
import com.littlehorizons.erp.model.entity.Teacher;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTeacherDao implements TeacherDao {

    /** Columns of profesores (alias p) that RowMapper.teacher needs; reused by the joins of other DAOs. */
    static final String TEACHER_COLUMNS = "p.id_profesor, p.tipo_documento, p.numero_documento, p.primer_nombre, "
            + "p.segundo_nombre, p.primer_apellido, p.segundo_apellido, p.correo_electronico, p.telefono, "
            + "p.especializacion, p.fecha_contratacion, p.estado";

    private static final String SELECT = "SELECT " + TEACHER_COLUMNS + " FROM profesores p";
    private static final String ORDER = " ORDER BY p.primer_apellido, p.segundo_apellido, p.primer_nombre";

    private static final String INSERT = "INSERT INTO profesores (tipo_documento, numero_documento, primer_nombre, "
            + "segundo_nombre, primer_apellido, segundo_apellido, correo_electronico, telefono, especializacion, "
            + "fecha_contratacion, estado) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE = "UPDATE profesores SET primer_nombre = ?, segundo_nombre = ?, "
            + "primer_apellido = ?, segundo_apellido = ?, correo_electronico = ?, telefono = ?, "
            + "especializacion = ?, estado = ? WHERE id_profesor = ?";

    private final DatabaseConnection database;

    JdbcTeacherDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Teacher teacher) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, teacher.getDocumentType().getDbLabel());
            statement.setString(2, teacher.getDocumentNumber());
            statement.setString(3, teacher.getFirstName());
            statement.setString(4, teacher.getMiddleName());
            statement.setString(5, teacher.getLastName());
            statement.setString(6, teacher.getSecondLastName());
            statement.setString(7, teacher.getEmail());
            statement.setString(8, teacher.getPhone());
            statement.setString(9, teacher.getSpecialization());
            statement.setDate(10, Date.valueOf(teacher.getHireDate()));
            statement.setString(11, teacher.getStatus().getDbLabel());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    teacher.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void update(Teacher teacher) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, teacher.getFirstName());
            statement.setString(2, teacher.getMiddleName());
            statement.setString(3, teacher.getLastName());
            statement.setString(4, teacher.getSecondLastName());
            statement.setString(5, teacher.getEmail());
            statement.setString(6, teacher.getPhone());
            statement.setString(7, teacher.getSpecialization());
            statement.setString(8, teacher.getStatus().getDbLabel());
            statement.setInt(9, teacher.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<Teacher> findByDocumentNumber(String documentNumber) throws DaoException {
        return query(SELECT + " WHERE p.numero_documento = ?", documentNumber).stream().findFirst();
    }

    @Override
    public Optional<Teacher> findByEmail(String email) throws DaoException {
        return query(SELECT + " WHERE p.correo_electronico = ?", email).stream().findFirst();
    }

    @Override
    public List<Teacher> findAll() throws DaoException {
        return query(SELECT + ORDER);
    }

    @Override
    public List<Teacher> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query(SELECT + " WHERE LOWER(CONCAT_WS(' ', p.primer_nombre, p.segundo_nombre, p.primer_apellido, "
                + "p.segundo_apellido, p.numero_documento, p.especializacion)) LIKE ?" + ORDER, "%" + escaped + "%");
    }

    private List<Teacher> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Teacher> teachers = new ArrayList<>();
                while (rs.next()) {
                    teachers.add(RowMapper.teacher(rs));
                }
                return teachers;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
