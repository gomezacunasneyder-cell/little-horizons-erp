package test.vista;

/**
 * Se lanza cuando el teclado ya no entrega más líneas (el usuario cerró la entrada con
 * Ctrl+Z / Ctrl+D, o una entrada redirigida llegó a su fin). Sin ella, el menú se quedaría
 * repitiéndose para siempre. Es "unchecked" porque no es un error del programa: solo es la
 * señal para terminar con calma.
 */
public class EntradaCerradaException extends RuntimeException {

    public EntradaCerradaException() {
        super("La entrada de teclado se cerró.");
    }
}
