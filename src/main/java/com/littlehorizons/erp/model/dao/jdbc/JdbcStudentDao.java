package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;

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

public class JdbcStudentDao implements StudentDao {

    /** Columns of estudiantes (alias e) that RowMapper.student needs. Reused by joins in other DAOs. */
    static final String STUDENT_COLUMNS = "e.id_estudiante, e.tipo_documento, e.numero_documento, e.primer_nombre, "
            + "e.segundo_nombre, e.primer_apellido, e.segundo_apellido, e.fecha_nacimiento, e.genero, "
            + "e.direccion, e.telefono, e.correo_electronico, e.tipo_sangre, e.alergias, "
            + "e.condiciones_medicas, e.fecha_ingreso, e.estado";

    private static final String SELECT = "SELECT " + STUDENT_COLUMNS + " FROM estudiantes e";
    private static final String ORDER = " ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre";

    private static final String INSERT_STUDENT = "INSERT INTO estudiantes (tipo_documento, numero_documento, "
            + "primer_nombre, segundo_nombre, primer_apellido, segundo_apellido, fecha_nacimiento, genero, "
            + "direccion, telefono, correo_electronico, tipo_sangre, alergias, condiciones_medicas, "
            + "fecha_ingreso, estado) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_STUDENT = "UPDATE estudiantes SET primer_nombre = ?, segundo_nombre = ?, "
            + "primer_apellido = ?, segundo_apellido = ?, fecha_nacimiento = ?, genero = ?, direccion = ?, "
            + "telefono = ?, correo_electronico = ?, tipo_sangre = ?, alergias = ?, condiciones_medicas = ?, "
            + "estado = ? WHERE id_estudiante = ?";

    private static final String INSERT_GUARDIAN = "INSERT INTO acudientes (tipo_documento, numero_documento, "
            + "primer_nombre, segundo_nombre, primer_apellido, segundo_apellido, parentesco, "
            + "telefono_principal, telefono_secundario, correo_electronico, direccion, ocupacion, "
            + "es_responsable_financiero) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_LINK = "INSERT INTO estudiante_acudiente (id_estudiante, id_acudiente, "
            + "es_principal, es_contacto_emergencia) VALUES (?,?,?,?)";

    private final DatabaseConnection database;

    JdbcStudentDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void create(Student student, List<StudentGuardian> guardians) throws DaoException {
        Transactions.run(database, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_STUDENT, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, student.getDocumentType().getDbLabel());
                statement.setString(2, student.getDocumentNumber());
                statement.setString(3, student.getFirstName());
                statement.setString(4, student.getMiddleName());
                statement.setString(5, student.getLastName());
                statement.setString(6, student.getSecondLastName());
                statement.setDate(7, Date.valueOf(student.getBirthDate()));
                statement.setString(8, student.getGender().getDbLabel());
                statement.setString(9, student.getAddress());
                statement.setString(10, student.getPhone());
                statement.setString(11, student.getEmail());
                statement.setString(12, student.getBloodType());
                statement.setString(13, student.getAllergies());
                statement.setString(14, student.getMedicalConditions());
                statement.setDate(15, Date.valueOf(student.getAdmissionDate()));
                statement.setString(16, student.getStatus().getDbLabel());
                statement.executeUpdate();
                student.setId(generatedKey(statement));
            }
            for (StudentGuardian link : guardians) {
                linkGuardian(connection, student.getId(), link);
            }
        });
    }

    @Override
    public void update(Student student) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_STUDENT)) {
            statement.setString(1, student.getFirstName());
            statement.setString(2, student.getMiddleName());
            statement.setString(3, student.getLastName());
            statement.setString(4, student.getSecondLastName());
            statement.setDate(5, Date.valueOf(student.getBirthDate()));
            statement.setString(6, student.getGender().getDbLabel());
            statement.setString(7, student.getAddress());
            statement.setString(8, student.getPhone());
            statement.setString(9, student.getEmail());
            statement.setString(10, student.getBloodType());
            statement.setString(11, student.getAllergies());
            statement.setString(12, student.getMedicalConditions());
            statement.setString(13, student.getStatus().getDbLabel());
            statement.setInt(14, student.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public Optional<Student> findById(int id) throws DaoException {
        return queryStudents(SELECT + " WHERE e.id_estudiante = ?", id).stream().findFirst();
    }

    @Override
    public Optional<Student> findByDocumentNumber(String documentNumber) throws DaoException {
        return queryStudents(SELECT + " WHERE e.numero_documento = ?", documentNumber).stream().findFirst();
    }

    @Override
    public List<Student> findAll() throws DaoException {
        return queryStudents(SELECT + ORDER);
    }

    @Override
    public List<Student> search(String term) throws DaoException {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return queryStudents(SELECT + " WHERE LOWER(CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, "
                + "e.primer_apellido, e.segundo_apellido, e.numero_documento)) LIKE ?" + ORDER, "%" + escaped + "%");
    }

    @Override
    public List<Student> findByGuardian(int guardianId) throws DaoException {
        return queryStudents(SELECT + " JOIN estudiante_acudiente ea ON ea.id_estudiante = e.id_estudiante "
                + "WHERE ea.id_acudiente = ?" + ORDER, guardianId);
    }

    @Override
    public List<StudentGuardian> findGuardians(int studentId) throws DaoException {
        String sql = "SELECT a.*, ea.es_principal, ea.es_contacto_emergencia FROM estudiante_acudiente ea "
                + "JOIN acudientes a ON a.id_acudiente = ea.id_acudiente WHERE ea.id_estudiante = ? "
                + "ORDER BY ea.es_principal DESC, a.primer_apellido";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                List<StudentGuardian> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(new StudentGuardian(RowMapper.guardian(rs),
                            rs.getBoolean("es_principal"), rs.getBoolean("es_contacto_emergencia")));
                }
                return links;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }

    @Override
    public void addGuardian(int studentId, StudentGuardian link) throws DaoException {
        Transactions.run(database, connection -> linkGuardian(connection, studentId, link));
    }

    @Override
    public void removeGuardian(int studentId, int guardianId) throws DaoException {
        Transactions.run(database, connection -> {
            int linked = 0;
            // FOR UPDATE locks the student's links so two removals cannot both pass the check
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM estudiante_acudiente WHERE id_estudiante = ? FOR UPDATE")) {
                count.setInt(1, studentId);
                try (ResultSet rs = count.executeQuery()) {
                    if (rs.next()) {
                        linked = rs.getInt(1);
                    }
                }
            }
            if (linked <= 1) {
                throw new DaoException("El estudiante debe conservar al menos un acudiente.");
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM estudiante_acudiente WHERE id_estudiante = ? AND id_acudiente = ?")) {
                delete.setInt(1, studentId);
                delete.setInt(2, guardianId);
                if (delete.executeUpdate() == 0) {
                    throw new DaoException("Ese acudiente no está asociado al estudiante.");
                }
            }
        });
    }

    @Override
    public void setPrincipalGuardian(int studentId, int guardianId) throws DaoException {
        Transactions.run(database, connection -> {
            try (PreparedStatement clear = connection.prepareStatement(
                    "UPDATE estudiante_acudiente SET es_principal = FALSE WHERE id_estudiante = ?")) {
                clear.setInt(1, studentId);
                clear.executeUpdate();
            }
            try (PreparedStatement set = connection.prepareStatement(
                    "UPDATE estudiante_acudiente SET es_principal = TRUE WHERE id_estudiante = ? AND id_acudiente = ?")) {
                set.setInt(1, studentId);
                set.setInt(2, guardianId);
                if (set.executeUpdate() == 0) {
                    throw new DaoException("Ese acudiente no está asociado al estudiante.");
                }
            }
        });
    }

    @Override
    public void retire(int studentId, LocalDate date) throws DaoException {
        Transactions.run(database, connection -> {
            try (PreparedStatement student = connection.prepareStatement(
                    "UPDATE estudiantes SET estado = 'Retirado' WHERE id_estudiante = ?")) {
                student.setInt(1, studentId);
                if (student.executeUpdate() == 0) {
                    throw new DaoException("El estudiante no existe.");
                }
            }
            try (PreparedStatement enrollments = connection.prepareStatement(
                    "UPDATE matriculas SET estado_matricula = 'Retirada', fecha_retiro = ? "
                            + "WHERE id_estudiante = ? AND estado_matricula = 'Activa'")) {
                enrollments.setDate(1, Date.valueOf(date));
                enrollments.setInt(2, studentId);
                enrollments.executeUpdate();
            }
            try (PreparedStatement registrations = connection.prepareStatement(
                    "UPDATE inscripciones_materia SET estado = 'Retirada' "
                            + "WHERE id_estudiante = ? AND estado = 'Activa'")) {
                registrations.setInt(1, studentId);
                registrations.executeUpdate();
            }
        });
    }

    // ---- private helpers ----

    /** Inserts the guardian if it is new (no id yet) and then the student-guardian link. */
    private void linkGuardian(Connection connection, int studentId, StudentGuardian link)
            throws SQLException {
        Guardian guardian = link.getGuardian();
        if (guardian.getId() == null) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_GUARDIAN, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, guardian.getDocumentType().getDbLabel());
                statement.setString(2, guardian.getDocumentNumber());
                statement.setString(3, guardian.getFirstName());
                statement.setString(4, guardian.getMiddleName());
                statement.setString(5, guardian.getLastName());
                statement.setString(6, guardian.getSecondLastName());
                statement.setString(7, guardian.getRelationship());
                statement.setString(8, guardian.getPhone());
                statement.setString(9, guardian.getSecondaryPhone());
                statement.setString(10, guardian.getEmail());
                statement.setString(11, guardian.getAddress());
                statement.setString(12, guardian.getOccupation());
                statement.setBoolean(13, guardian.isFinanciallyResponsible());
                statement.executeUpdate();
                guardian.setId(generatedKey(statement));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LINK)) {
            statement.setInt(1, studentId);
            statement.setInt(2, guardian.getId());
            statement.setBoolean(3, link.isPrincipal());
            statement.setBoolean(4, link.isEmergencyContact());
            statement.executeUpdate();
        }
    }

    private static int generatedKey(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
            throw new SQLException("The database did not return the generated id.");
        }
    }

    private List<Student> queryStudents(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Student> students = new ArrayList<>();
                while (rs.next()) {
                    students.add(RowMapper.student(rs));
                }
                return students;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
