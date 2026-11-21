package test.vista;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Toda la entrada y salida de consola del buscador pasa por esta clase: es la única que usa
 * Scanner y System.in. Así el resto de la vista solo dice QUÉ mostrar, no CÓMO. Recibe el
 * PrintStream y el Scanner por el constructor para poder usarla también con entradas de prueba.
 */
public final class ConsolaBuscador {

    /** Línea de 79 caracteres, el ancho que usa el enunciado. */
    public static final String SEPARADOR_DOBLE = "=".repeat(79);
    public static final String SEPARADOR_SIMPLE = "-".repeat(79);

    private final PrintStream salida;
    private final Scanner entrada;

    public ConsolaBuscador(PrintStream salida, Scanner entrada) {
        this.salida = salida;
        this.entrada = entrada;
    }

    /** Consola real: teclado y pantalla en UTF-8, para que las tildes no salgan rotas en Windows. */
    public static ConsolaBuscador estandar() {
        PrintStream pantalla = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        return new ConsolaBuscador(pantalla, new Scanner(System.in, StandardCharsets.UTF_8));
    }

    public void imprimir(String texto) {
        salida.println(texto);
    }

    public void lineaEnBlanco() {
        salida.println();
    }

    /** Muestra la pregunta sin salto de línea y devuelve lo que escribió el usuario, sin espacios en los extremos. */
    public String leerLinea(String pregunta) {
        salida.print(pregunta);
        salida.flush();
        return leer();
    }

    /** Muestra el mensaje y espera a que el usuario presione Enter. */
    public void pausar(String mensaje) {
        salida.println(mensaje);
        esperarEnter();
    }

    /** Espera Enter sin mostrar nada (el mensaje ya se imprimió antes). */
    public void esperarEnter() {
        leer();
    }

    private String leer() {
        if (!entrada.hasNextLine()) {
            throw new EntradaCerradaException();
        }
        return entrada.nextLine().trim();
    }
}
