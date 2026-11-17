package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.LoanDao;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.LoanStatus;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loans of students and of teachers. Both tables have the same columns, so the only thing that
 * changes between them is a few names (see {@link Source}); the SQL is written once and built for
 * each table, and the lists that mix both are a UNION ALL of the two selects.
 */
public class JdbcLoanDao implements LoanDao {

    /** The names that differ between the two loan tables: the loan table, its borrower column and the person table. */
    private record Source(String loanTable, String borrowerColumn, String personTable, String personIdColumn) {
    }

    private static final Map<BorrowerType, Source> SOURCES = Map.of(
            BorrowerType.STUDENT, new Source("prestamos_estudiante", "id_estudiante", "estudiantes", "id_estudiante"),
            BorrowerType.TEACHER, new Source("prestamos_profesor", "id_profesor", "profesores", "id_profesor"));

    private static final String OPEN_STATES = "l.estado_prestamo IN (?, ?)";

    private final DatabaseConnection database;

    JdbcLoanDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public boolean lend(Loan loan) throws DaoException {
        Source source = SOURCES.get(loan.getBorrowerType());
        AtomicBoolean lent = new AtomicBoolean(false);
        Transactions.run(database, connection -> {
            if (!updateResourceStatus(connection, loan.getResource().getId(), ResourceStatus.AVAILABLE, ResourceStatus.BORROWED)) {
                return; // not available any more: nothing was changed, so there is nothing to undo
            }
            String sql = "INSERT INTO " + source.loanTable() + " (id_recurso, " + source.borrowerColumn()
                    + ", fecha_prestamo, fecha_devolucion_esperada, estado_prestamo, observaciones) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, loan.getResource().getId());
                statement.setInt(2, loan.getBorrower().id());
                statement.setDate(3, Date.valueOf(loan.getLoanDate()));
                statement.setDate(4, Date.valueOf(loan.getDueDate()));
                statement.setString(5, loan.getStatus().getDbLabel());
                statement.setString(6, loan.getObservations());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        loan.setId(keys.getInt(1));
                    }
                }
            }
            lent.set(true);
        });
        return lent.get();
    }

    @Override
    public boolean giveBack(Loan loan) throws DaoException {
        Source source = SOURCES.get(loan.getBorrowerType());
        AtomicBoolean returned = new AtomicBoolean(false);
        Transactions.run(database, connection -> {
            String sql = "UPDATE " + source.loanTable() + " SET fecha_devolucion_real = ?, estado_prestamo = ?, "
                    + "observaciones = ? WHERE id_prestamo = ? AND estado_prestamo IN (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDate(1, Date.valueOf(loan.getReturnDate()));
                statement.setString(2, loan.getStatus().getDbLabel());
                statement.setString(3, loan.getObservations());
                statement.setInt(4, loan.getId());
                statement.setString(5, LoanStatus.ACTIVE.getDbLabel());
                statement.setString(6, LoanStatus.OVERDUE.getDbLabel());
                if (statement.executeUpdate() == 0) {
                    return; // somebody returned it first
                }
            }
            // Lenient on purpose: if the resource is not "Prestado" (it was edited by hand), the return still counts.
            updateResourceStatus(connection, loan.getResource().getId(), ResourceStatus.BORROWED, ResourceStatus.AVAILABLE);
            returned.set(true);
        });
        return returned.get();
    }

    @Override
    public Optional<Loan> findOpenByResource(int resourceId) throws DaoException {
        return queryBoth("WHERE l.id_recurso = ? AND " + OPEN_STATES, "fecha_prestamo DESC", resourceId,
                LoanStatus.ACTIVE.getDbLabel(), LoanStatus.OVERDUE.getDbLabel()).stream().findFirst();
    }

    @Override
    public List<Loan> findOpen() throws DaoException {
        return queryBoth("WHERE " + OPEN_STATES, "fecha_devolucion_esperada, fecha_prestamo, id_prestamo",
                LoanStatus.ACTIVE.getDbLabel(), LoanStatus.OVERDUE.getDbLabel());
    }

    @Override
    public List<Loan> findOverdue(LocalDate today) throws DaoException {
        return queryBoth("WHERE " + OPEN_STATES + " AND l.fecha_devolucion_esperada < ?",
                "fecha_devolucion_esperada, fecha_prestamo, id_prestamo",
                LoanStatus.ACTIVE.getDbLabel(), LoanStatus.OVERDUE.getDbLabel(), Date.valueOf(today));
    }

    @Override
    public List<Loan> findByResource(int resourceId) throws DaoException {
        return queryBoth("WHERE l.id_recurso = ?", "fecha_prestamo DESC, id_prestamo DESC", resourceId);
    }

    @Override
    public List<Loan> findByBorrower(BorrowerType type, int borrowerId) throws DaoException {
        Source source = SOURCES.get(type);
        return query(select(type) + "WHERE l." + source.borrowerColumn() + " = ? "
                + "ORDER BY l.fecha_prestamo DESC, l.id_prestamo DESC", borrowerId);
    }

    @Override
    public int markOverdue(LocalDate today) throws DaoException {
        int changed = 0;
        try (Connection connection = database.getConnection()) {
            for (BorrowerType type : BorrowerType.values()) {
                String sql = "UPDATE " + SOURCES.get(type).loanTable() + " SET estado_prestamo = ? "
                        + "WHERE estado_prestamo = ? AND fecha_devolucion_esperada < ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, LoanStatus.OVERDUE.getDbLabel());
                    statement.setString(2, LoanStatus.ACTIVE.getDbLabel());
                    statement.setDate(3, Date.valueOf(today));
                    changed += statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
        return changed;
    }

    /** Guarded UPDATE of the state of a resource: it only changes if the resource is still in the "from" state. */
    private static boolean updateResourceStatus(Connection connection, int resourceId, ResourceStatus from,
                                                ResourceStatus to) throws SQLException {
        String sql = "UPDATE recursos SET estado_recurso = ? WHERE id_recurso = ? AND estado_recurso = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, to.getDbLabel());
            statement.setInt(2, resourceId);
            statement.setString(3, from.getDbLabel());
            return statement.executeUpdate() > 0;
        }
    }

    /** The select of the loans of ONE kind of borrower, with the resource and the borrower joined. */
    private static String select(BorrowerType type) {
        Source s = SOURCES.get(type);
        return "SELECT '" + type.getDbLabel() + "' AS tipo_prestatario, l.id_prestamo, l.fecha_prestamo, "
                + "l.fecha_devolucion_esperada, l.fecha_devolucion_real, l.estado_prestamo, l.observaciones, "
                + "b." + s.personIdColumn() + " AS id_prestatario, "
                + "TRIM(CONCAT_WS(' ', b.primer_nombre, b.segundo_nombre, b.primer_apellido, b.segundo_apellido)) AS nombre_prestatario, "
                + "b.numero_documento, b.correo_electronico, b.telefono, b.estado AS estado_prestatario, "
                + JdbcResourceDao.RESOURCE_COLUMNS
                + " FROM " + s.loanTable() + " l "
                + "JOIN " + s.personTable() + " b ON b." + s.personIdColumn() + " = l." + s.borrowerColumn() + " "
                + "JOIN recursos r ON r.id_recurso = l.id_recurso "
                + "JOIN categorias_recurso c ON c.id_categoria = r.id_categoria ";
    }

    /**
     * Runs the same WHERE on both loan tables and joins the rows (UNION ALL). The parameters are
     * given ONCE and repeated for the second select. In a UNION the ORDER BY can only use column
     * names of the select, which is why it is written without the "l." alias.
     */
    private List<Loan> queryBoth(String where, String orderBy, Object... parameters) throws DaoException {
        String sql = "(" + select(BorrowerType.STUDENT) + where + ") UNION ALL (" + select(BorrowerType.TEACHER)
                + where + ") ORDER BY " + orderBy;
        Object[] twice = new Object[parameters.length * 2];
        System.arraycopy(parameters, 0, twice, 0, parameters.length);
        System.arraycopy(parameters, 0, twice, parameters.length, parameters.length);
        return query(sql, twice);
    }

    private List<Loan> query(String sql, Object... parameters) throws DaoException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Loan> loans = new ArrayList<>();
                while (rs.next()) {
                    loans.add(RowMapper.loan(rs));
                }
                return loans;
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
    }
}
