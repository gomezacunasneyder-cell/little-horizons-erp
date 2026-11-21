package test.vista;

import com.littlehorizons.erp.exception.DaoException;
import test.controlador.BuscadorEstudiantesController;
import test.modelo.EstudianteMatriculado;
import test.modelo.GradoAcademico;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static test.vista.ConsolaBuscador.SEPARADOR_DOBLE;

/**
 * Menú del buscador y flujo de la opción "Filtrar por grado académico".
 * La vista pide datos, los valida y muestra mensajes; la búsqueda real la hace el controlador.
 */
public class MenuBuscadorVista {

    private static final int OPCION_SALIR = 0;
    private static final int OPCION_POR_GRADO = 1;
    private static final int ULTIMA_OPCION = 5;
    private static final int CANCELAR = 0;

    private static final String VOLVER_AL_MENU = "Presione Enter para volver al menú principal...";
    private static final String INTENTAR_NUEVAMENTE = "Presione Enter para intentar nuevamente...";

    private final BuscadorEstudiantesController controlador;
    private final ConsolaBuscador consola;
    private final ResultadosBusquedaVista resultadosVista;

    public MenuBuscadorVista(BuscadorEstudiantesController controlador, ConsolaBuscador consola) {
        this.controlador = controlador;
        this.consola = consola;
        this.resultadosVista = new ResultadosBusquedaVista(consola);
    }

    /** Repite el menú hasta que el usuario elige 0 (Salir) o se cierra el teclado. */
    public void iniciar() {
        try {
            boolean salir = false;
            while (!salir) {
                mostrarMenu();
                salir = procesarOpcion(consola.leerLinea("Seleccione una opción: "));
            }
        } catch (EntradaCerradaException e) {
            // El teclado se cerró (Ctrl+Z / Ctrl+D): salimos con normalidad.
        }
        despedirse();
    }

    private void mostrarMenu() {
        consola.lineaEnBlanco();
        consola.imprimir(SEPARADOR_DOBLE);
        consola.imprimir("            BUSCADOR AVANZADO DE ESTUDIANTES - PROTOTIPO");
        consola.imprimir(SEPARADOR_DOBLE);
        consola.lineaEnBlanco();
        consola.imprimir("Seleccione el filtro que desea aplicar:");
        consola.lineaEnBlanco();
        consola.imprimir("1. Filtrar por grado académico [FUNCIONAL]");
        consola.imprimir("2. Filtrar por rango de edad [NO IMPLEMENTADO]");
        consola.imprimir("3. Filtrar por género [NO IMPLEMENTADO]");
        consola.imprimir("4. Filtrar por estado de matrícula [NO IMPLEMENTADO]");
        consola.imprimir("5. Filtrar por rango de promedio académico [NO IMPLEMENTADO]");
        consola.imprimir("0. Salir");
        consola.lineaEnBlanco();
    }

    /** @return true si el usuario pidió salir */
    private boolean procesarOpcion(String texto) {
        OptionalInt numero = convertirAEntero(texto);
        if (numero.isEmpty() || numero.getAsInt() < OPCION_SALIR || numero.getAsInt() > ULTIMA_OPCION) {
            consola.lineaEnBlanco();
            consola.imprimir("Error: Debe seleccionar una opción entre " + OPCION_SALIR + " y " + ULTIMA_OPCION + ".");
            consola.pausar("Presione Enter para continuar...");
            return false;
        }
        int opcion = numero.getAsInt();
        if (opcion == OPCION_SALIR) {
            return true;
        }
        if (opcion == OPCION_POR_GRADO) {
            filtrarPorGrado();
        } else {
            mostrarNoImplementado();   // opciones 2, 3, 4 y 5
        }
        return false;
    }

    private void mostrarNoImplementado() {
        consola.lineaEnBlanco();
        consola.imprimir("Esta funcionalidad aún no ha sido implementada.");
        consola.imprimir("Será agregada en futuras versiones del sistema.");
        consola.lineaEnBlanco();
        consola.pausar("Presione Enter para continuar...");
    }

    // ------------------------------------------------------------ opción 1: grado

    /** Pregunta el grado hasta recibir uno válido o un 0 (cancelar), y entonces busca. */
    private void filtrarPorGrado() {
        while (true) {
            mostrarListaDeGrados();
            OptionalInt numero = convertirAEntero(
                    consola.leerLinea("Ingrese el número del grado que desea filtrar (o 0 para cancelar): "));

            if (numero.isEmpty()) {
                consola.imprimir("Error: Debe ingresar un número válido.");
                consola.pausar(INTENTAR_NUEVAMENTE);
                continue;
            }
            if (numero.getAsInt() == CANCELAR) {
                consola.imprimir("Operación cancelada.");
                consola.pausar(VOLVER_AL_MENU);
                return;
            }
            Optional<GradoAcademico> grado = GradoAcademico.desdeNumero(numero.getAsInt());
            if (grado.isEmpty()) {
                consola.imprimir("Error: Debe seleccionar un número entre 1 y " + GradoAcademico.cantidad() + ".");
                consola.pausar(INTENTAR_NUEVAMENTE);
                continue;
            }
            buscarYMostrar(grado.get());
            return;
        }
    }

    private void mostrarListaDeGrados() {
        consola.lineaEnBlanco();
        consola.imprimir("--- FILTRAR POR GRADO ACADÉMICO ---");
        consola.lineaEnBlanco();
        consola.imprimir("Grados disponibles en el sistema:");
        for (GradoAcademico grado : GradoAcademico.values()) {
            consola.imprimir(grado.getNumero() + ". " + grado.getNombre());
        }
        consola.lineaEnBlanco();
    }

    private void buscarYMostrar(GradoAcademico grado) {
        consola.lineaEnBlanco();
        consola.imprimir("Procesando búsqueda...");
        consola.imprimir("Consultando estudiantes matriculados en " + grado.getNombre() + "...");
        try {
            List<EstudianteMatriculado> resultados = controlador.buscarPorGrado(grado);
            resultadosVista.mostrar(grado, BuscadorEstudiantesController.ANIO_ACADEMICO, resultados);
        } catch (DaoException e) {
            consola.lineaEnBlanco();
            consola.imprimir("Error: " + e.getMessage());
            consola.pausar(VOLVER_AL_MENU);
        }
    }

    // ------------------------------------------------------------ utilidades

    private void despedirse() {
        consola.lineaEnBlanco();
        consola.imprimir("Saliendo del buscador...");
        consola.imprimir("Gracias por usar el sistema.");
    }

    /** Convierte el texto a entero; vacío si no es un número entero (letras, decimales, texto vacío). */
    private OptionalInt convertirAEntero(String texto) {
        try {
            return OptionalInt.of(Integer.parseInt(texto));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
