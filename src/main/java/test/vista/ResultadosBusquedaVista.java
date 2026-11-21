package test.vista;

import test.modelo.EstudianteMatriculado;
import test.modelo.GradoAcademico;

import java.util.List;

import static test.vista.ConsolaBuscador.SEPARADOR_DOBLE;
import static test.vista.ConsolaBuscador.SEPARADOR_SIMPLE;

/**
 * Presentación de los resultados de la búsqueda por grado, con el formato del enunciado.
 * Solo imprime: no consulta la base de datos ni filtra nada.
 */
public class ResultadosBusquedaVista {

    private final ConsolaBuscador consola;

    public ResultadosBusquedaVista(ConsolaBuscador consola) {
        this.consola = consola;
    }

    public void mostrar(GradoAcademico grado, int anioAcademico, List<EstudianteMatriculado> resultados) {
        consola.lineaEnBlanco();
        consola.imprimir(SEPARADOR_DOBLE);
        consola.imprimir("            RESULTADOS DE LA BÚSQUEDA");
        consola.imprimir(SEPARADOR_DOBLE);
        consola.lineaEnBlanco();
        consola.imprimir("Filtro aplicado: Grado académico = " + grado.getNombre());
        consola.lineaEnBlanco();

        if (resultados.isEmpty()) {
            mostrarSinResultados(grado, anioAcademico);
        } else {
            mostrarConResultados(grado, anioAcademico, resultados);
        }

        consola.imprimir(SEPARADOR_DOBLE);
        consola.imprimir("Presione Enter para volver al menú principal...");
        consola.imprimir(SEPARADOR_DOBLE);
        consola.esperarEnter();
    }

    private void mostrarConResultados(GradoAcademico grado, int anioAcademico, List<EstudianteMatriculado> resultados) {
        int total = resultados.size();
        boolean uno = total == 1;
        consola.imprimir((uno ? "Se encontró 1 estudiante matriculado en " : "Se encontraron " + total + " estudiantes matriculados en ")
                + grado.getNombre() + " (año " + anioAcademico + "):");
        consola.lineaEnBlanco();

        consola.imprimir(SEPARADOR_SIMPLE);
        for (EstudianteMatriculado estudiante : resultados) {
            consola.imprimir("ID: " + estudiante.idEstudiante());
            consola.imprimir("Nombre: " + estudiante.nombreCompleto());
            consola.imprimir("Documento: " + estudiante.documentoCompleto());
            consola.imprimir(SEPARADOR_SIMPLE);
        }

        consola.lineaEnBlanco();
        consola.imprimir("Total de resultados: " + total + (uno ? " estudiante" : " estudiantes"));
        consola.lineaEnBlanco();
    }

    private void mostrarSinResultados(GradoAcademico grado, int anioAcademico) {
        consola.imprimir("No se encontraron estudiantes matriculados en " + grado.getNombre()
                + " para el año " + anioAcademico + ".");
        consola.lineaEnBlanco();
        consola.imprimir("Posibles razones:");
        consola.imprimir("  - No hay estudiantes matriculados en ese grado para el año " + anioAcademico);
        consola.imprimir("  - Verifique que la base de datos tenga información del año actual");
        consola.lineaEnBlanco();
    }
}
