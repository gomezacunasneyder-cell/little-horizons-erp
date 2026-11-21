package test.modelo;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DTO (objeto de transferencia de datos) que junta lo que el buscador necesita de las tres
 * tablas: estudiantes (nombre y documento), matriculas (id del grado) y grados (nombre del grado).
 * Es un record: inmutable, y Java genera constructor, getters (idEstudiante(), ...), equals y toString.
 *
 * Los segundos nombres y apellidos son opcionales en la base de datos, por eso pueden ser null.
 */
public record EstudianteMatriculado(
        int idEstudiante,
        String tipoDocumento,
        String numeroDocumento,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        int idGrado,
        String nombreGrado) {

    /**
     * Nombre completo solo con las partes que existen: si el segundo nombre o el segundo apellido
     * es null (o está vacío) se omite, así nunca quedan espacios dobles.
     */
    public String nombreCompleto() {
        return Stream.of(primerNombre, segundoNombre, primerApellido, segundoApellido)
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    /** Documento como lo pide el enunciado: tipo + número, por ejemplo "TI 1023456789". */
    public String documentoCompleto() {
        return tipoDocumento + " " + numeroDocumento;
    }
}
