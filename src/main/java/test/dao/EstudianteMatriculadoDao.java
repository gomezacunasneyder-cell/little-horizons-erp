package test.dao;

import com.littlehorizons.erp.exception.DaoException;
import test.modelo.EstudianteMatriculado;

import java.util.List;

/**
 * Contrato de acceso a datos del buscador (mismo patrón DAO del ERP: una interfaz y una
 * implementación JDBC). El controlador depende de esta interfaz, no de JDBC, así la lógica del
 * buscador se puede probar sin base de datos.
 */
public interface EstudianteMatriculadoDao {

    /**
     * Estudiantes con matrícula 'Activa' en el año académico dado, de TODOS los grados, juntando
     * los datos de estudiantes, matriculas y grados. El filtro por grado NO se hace aquí: lo hace
     * el controlador con Stream API.
     *
     * @throws DaoException si la base de datos no responde o la consulta falla
     */
    List<EstudianteMatriculado> buscarMatriculasActivas(int anioAcademico) throws DaoException;
}
