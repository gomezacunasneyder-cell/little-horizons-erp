package test.controlador;

import com.littlehorizons.erp.exception.DaoException;
import test.dao.EstudianteMatriculadoDao;
import test.modelo.EstudianteMatriculado;
import test.modelo.GradoAcademico;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador del buscador: coordina el flujo "pedir datos al DAO, filtrar, devolver a la vista".
 * No imprime ni lee del teclado (eso es de la vista) y no contiene SQL (eso es del DAO).
 */
public class BuscadorEstudiantesController {

    /** Año académico que consulta el examen. */
    public static final int ANIO_ACADEMICO = 2025;

    private final EstudianteMatriculadoDao dao;

    public BuscadorEstudiantesController(EstudianteMatriculadoDao dao) {
        this.dao = dao;
    }

    /**
     * Estudiantes con matrícula Activa en 2025 del grado dado.
     * Primero trae de la base de datos las matrículas activas de 2025 (de todos los grados)
     * y después aplica el filtro por grado con Stream API.
     *
     * @throws DaoException si la base de datos falla
     */
    public List<EstudianteMatriculado> buscarPorGrado(GradoAcademico grado) throws DaoException {
        List<EstudianteMatriculado> matriculadosActivos = dao.buscarMatriculasActivas(ANIO_ACADEMICO);
        return filtrarPorGrado(matriculadosActivos, grado);
    }

    /**
     * El filtro con Stream API, separado en su propio método para poder probarlo sin base de datos.
     *   stream()  -> convierte la lista en un flujo de elementos
     *   filter()  -> se queda solo con los que cumplen la condición (lambda)
     *   collect() -> junta los que pasaron en una nueva lista (la original no se modifica)
     */
    public List<EstudianteMatriculado> filtrarPorGrado(List<EstudianteMatriculado> estudiantes,
                                                       GradoAcademico grado) {
        return estudiantes.stream()
                .filter(estudiante -> estudiante.idGrado() == grado.getNumero())
                .collect(Collectors.toList());
    }
}
