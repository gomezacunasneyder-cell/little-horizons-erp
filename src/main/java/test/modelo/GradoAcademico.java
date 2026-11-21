package test.modelo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los 12 grados que muestra el menú del buscador.
 *
 * El número de cada grado coincide con grados.id_grado de la base de datos: docs/database.sql
 * los inserta en este mismo orden (Preescolar = 1 ... Once = 12). Por eso el número que
 * escribe el usuario sirve directamente para filtrar por matriculas.id_grado.
 */
public enum GradoAcademico {

    PREESCOLAR(1, "Preescolar"),
    PRIMERO(2, "Primero"),
    SEGUNDO(3, "Segundo"),
    TERCERO(4, "Tercero"),
    CUARTO(5, "Cuarto"),
    QUINTO(6, "Quinto"),
    SEXTO(7, "Sexto"),
    SEPTIMO(8, "Séptimo"),
    OCTAVO(9, "Octavo"),
    NOVENO(10, "Noveno"),
    DECIMO(11, "Décimo"),
    ONCE(12, "Once");

    private final int numero;
    private final String nombre;

    GradoAcademico(int numero, String nombre) {
        this.numero = numero;
        this.nombre = nombre;
    }

    public int getNumero() {
        return numero;
    }

    public String getNombre() {
        return nombre;
    }

    /** Cuántos grados hay (12). Sirve para el mensaje "entre 1 y 12" sin escribir el número a mano. */
    public static int cantidad() {
        return values().length;
    }

    /** Busca el grado por su número del menú; vacío si el número no corresponde a ningún grado. */
    public static Optional<GradoAcademico> desdeNumero(int numero) {
        return Arrays.stream(values())
                .filter(grado -> grado.numero == numero)
                .findFirst();
    }
}
