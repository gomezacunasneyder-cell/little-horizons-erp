package test.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.SqlErrorTranslator;
import test.modelo.EstudianteMatriculado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC del buscador. Reutiliza DatabaseConnection del ERP (config.txt, usuario y
 * contraseña): no se duplica la conexión. Este es el único lugar del paquete test que usa java.sql.
 */
public class JdbcEstudianteMatriculadoDao implements EstudianteMatriculadoDao {

    // El año entra como parámetro (?) con PreparedStatement: nada se concatena en el SQL,
    // así no hay inyección SQL. El estado 'Activa' es una constante de la consulta.
    private static final String SQL_MATRICULAS_ACTIVAS = """
            SELECT e.id_estudiante, e.tipo_documento, e.numero_documento,
                   e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido,
                   g.id_grado, g.nombre_grado
            FROM estudiantes e
            JOIN matriculas m ON m.id_estudiante = e.id_estudiante
            JOIN grados g ON g.id_grado = m.id_grado
            WHERE m.anio_academico = ?
              AND m.estado_matricula = 'Activa'
            ORDER BY e.id_estudiante
            """;

    @Override
    public List<EstudianteMatriculado> buscarMatriculasActivas(int anioAcademico) throws DaoException {
        List<EstudianteMatriculado> estudiantes = new ArrayList<>();
        // try-with-resources: la conexión, el statement y el resultset se cierran solos.
        try (Connection conexion = DatabaseConnection.getInstance().getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_MATRICULAS_ACTIVAS)) {
            sentencia.setInt(1, anioAcademico);
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    estudiantes.add(mapear(fila));
                }
            }
        } catch (SQLException e) {
            throw new DaoException(SqlErrorTranslator.toUserMessage(e), e);
        }
        return estudiantes;
    }

    /** Convierte la fila actual del ResultSet en un EstudianteMatriculado. Los segundos nombres pueden ser null. */
    private EstudianteMatriculado mapear(ResultSet fila) throws SQLException {
        return new EstudianteMatriculado(
                fila.getInt("id_estudiante"),
                fila.getString("tipo_documento"),
                fila.getString("numero_documento"),
                fila.getString("primer_nombre"),
                fila.getString("segundo_nombre"),
                fila.getString("primer_apellido"),
                fila.getString("segundo_apellido"),
                fila.getInt("id_grado"),
                fila.getString("nombre_grado"));
    }
}
