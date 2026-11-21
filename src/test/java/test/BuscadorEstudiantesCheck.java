package test;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import test.controlador.BuscadorEstudiantesController;
import test.dao.EstudianteMatriculadoDao;
import test.dao.JdbcEstudianteMatriculadoDao;
import test.modelo.EstudianteMatriculado;
import test.modelo.GradoAcademico;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Verificación manual del buscador (Java simple, sin framework, igual que los *Check del ERP).
 * Ejecuta su main() desde IntelliJ. La parte 1 no necesita base de datos; la parte 2 necesita
 * la base cargada y config.txt. Cada verificación imprime PASS o FAIL; si alguna falla, el
 * proceso termina con código 1.
 */
public final class BuscadorEstudiantesCheck {

    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== Parte 1: lógica del buscador (sin base de datos) ===");
        checksSinBaseDeDatos();

        System.out.println();
        System.out.println("=== Parte 2: consulta real a MySQL ===");
        checksConBaseDeDatos();

        System.out.println();
        if (failures == 0) {
            System.out.println("TODO OK");
        } else {
            System.out.println("FALLARON " + failures + " verificación(es)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ parte 1

    private static void checksSinBaseDeDatos() {
        check("nombre completo con los cuatro nombres", () ->
                estudiante(1, "1", "Juan", "Carlos", "Pérez", "González", 11)
                        .nombreCompleto().equals("Juan Carlos Pérez González"));

        check("sin segundo nombre: no quedan espacios dobles", () ->
                estudiante(3, "3", "Carlos", null, "Gómez", "Silva", 11)
                        .nombreCompleto().equals("Carlos Gómez Silva"));

        check("sin segundo nombre ni segundo apellido", () ->
                estudiante(5, "5", "Andrés", null, "Mejía", null, 10)
                        .nombreCompleto().equals("Andrés Mejía"));

        check("un segundo nombre en blanco también se omite", () ->
                estudiante(6, "6", "Ana", "   ", "Ruiz", "", 10)
                        .nombreCompleto().equals("Ana Ruiz"));

        check("documento = tipo + número (TI 1023456789)", () ->
                new EstudianteMatriculado(1, "TI", "1023456789", "Juan", null, "Pérez", null, 11, "Décimo")
                        .documentoCompleto().equals("TI 1023456789"));

        check("hay 12 grados y sus números van de 1 a 12 en orden", () -> {
            GradoAcademico[] grados = GradoAcademico.values();
            for (int i = 0; i < grados.length; i++) {
                if (grados[i].getNumero() != i + 1) {
                    return false;
                }
            }
            return GradoAcademico.cantidad() == 12;
        });

        check("1 = Preescolar, 11 = Décimo, 12 = Once", () ->
                GradoAcademico.desdeNumero(1).orElseThrow().getNombre().equals("Preescolar")
                        && GradoAcademico.desdeNumero(11).orElseThrow().getNombre().equals("Décimo")
                        && GradoAcademico.desdeNumero(12).orElseThrow().getNombre().equals("Once"));

        check("0 y 13 no son grados válidos", () ->
                GradoAcademico.desdeNumero(0).isEmpty() && GradoAcademico.desdeNumero(13).isEmpty()
                        && GradoAcademico.desdeNumero(-4).isEmpty());

        List<EstudianteMatriculado> todos = List.of(
                estudiante(1, "1", "Juan", "Carlos", "Pérez", "González", 11),
                estudiante(2, "2", "María", "Elena", "López", "Ramírez", 11),
                estudiante(5, "5", "Andrés", null, "Mejía", null, 10),
                estudiante(3, "3", "Carlos", null, "Gómez", "Silva", 11));
        BuscadorEstudiantesController controlador = new BuscadorEstudiantesController(anio -> todos);

        check("el filtro Stream deja solo el grado pedido y conserva el orden", () -> {
            List<EstudianteMatriculado> decimo = controlador.filtrarPorGrado(todos, GradoAcademico.DECIMO);
            return decimo.size() == 3
                    && decimo.get(0).idEstudiante() == 1
                    && decimo.get(1).idEstudiante() == 2
                    && decimo.get(2).idEstudiante() == 3;
        });

        check("un grado sin estudiantes da una lista vacía (no null)", () ->
                controlador.filtrarPorGrado(todos, GradoAcademico.ONCE).isEmpty());

        check("el filtro no modifica la lista original", () -> {
            controlador.filtrarPorGrado(todos, GradoAcademico.NOVENO);
            return todos.size() == 4;
        });

        check("buscarPorGrado le pide al DAO el año 2025", () -> {
            List<Integer> aniosPedidos = new ArrayList<>();
            EstudianteMatriculadoDao espia = anio -> {
                aniosPedidos.add(anio);
                return todos;
            };
            new BuscadorEstudiantesController(espia).buscarPorGrado(GradoAcademico.DECIMO);
            return aniosPedidos.equals(List.of(2025));
        });
    }

    // ------------------------------------------------------------------ parte 2

    private static void checksConBaseDeDatos() {
        EstudianteMatriculadoDao dao = new JdbcEstudianteMatriculadoDao();
        BuscadorEstudiantesController controlador = new BuscadorEstudiantesController(dao);

        check("la consulta trae exactamente las matrículas Activas de 2025 (contra un COUNT independiente)", () ->
                dao.buscarMatriculasActivas(2025).size()
                        == contar("SELECT COUNT(*) FROM matriculas WHERE anio_academico = 2025 AND estado_matricula = 'Activa'"));

        check("para cada grado 1..12, el filtro Stream devuelve tantos como un COUNT por id_grado", () -> {
            for (GradoAcademico grado : GradoAcademico.values()) {
                int esperados = contar("SELECT COUNT(*) FROM matriculas WHERE anio_academico = 2025"
                        + " AND estado_matricula = 'Activa' AND id_grado = " + grado.getNumero());
                List<EstudianteMatriculado> encontrados = controlador.buscarPorGrado(grado);
                boolean mismoGrado = encontrados.stream().allMatch(e -> e.idGrado() == grado.getNumero());
                if (encontrados.size() != esperados || !mismoGrado) {
                    return false;
                }
            }
            return true;
        });

        check("el nombre del grado que trae el JOIN coincide con el del menú", () -> {
            for (GradoAcademico grado : GradoAcademico.values()) {
                for (EstudianteMatriculado e : controlador.buscarPorGrado(grado)) {
                    if (!e.nombreGrado().equals(grado.getNombre())) {
                        return false;
                    }
                }
            }
            return true;
        });

        check("la matrícula Retirada de 2025 (1023456792) nunca aparece en Décimo", () ->
                controlador.buscarPorGrado(GradoAcademico.DECIMO).stream()
                        .noneMatch(e -> e.numeroDocumento().equals("1023456792")));

        check("con docs/buscador-test-data.sql cargado, Décimo trae a los 3 estudiantes del ejemplo", () -> {
            List<String> documentos = controlador.buscarPorGrado(GradoAcademico.DECIMO).stream()
                    .map(EstudianteMatriculado::numeroDocumento)
                    .toList();
            return documentos.containsAll(List.of("1023456789", "1023456790", "1023456791"));
        });
    }

    // ------------------------------------------------------------------ utilidades

    private static EstudianteMatriculado estudiante(int id, String documento, String primerNombre,
                                                    String segundoNombre, String primerApellido,
                                                    String segundoApellido, int idGrado) {
        return new EstudianteMatriculado(id, "TI", documento, primerNombre, segundoNombre,
                primerApellido, segundoApellido, idGrado, "grado " + idGrado);
    }

    /** Ejecuta un SELECT COUNT(*) con SQL propio de la verificación (independiente del DAO). */
    private static int contar(String sql) throws DaoException, SQLException {
        try (Connection conexion = DatabaseConnection.getInstance().getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet fila = sentencia.executeQuery()) {
            fila.next();
            return fila.getInt(1);
        }
    }

    private static void check(String nombre, Callable<Boolean> prueba) {
        try {
            if (prueba.call()) {
                System.out.println("PASS  " + nombre);
            } else {
                failures++;
                System.out.println("FAIL  " + nombre);
            }
        } catch (Exception e) {
            failures++;
            System.out.println("FAIL  " + nombre + "  ->  " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
