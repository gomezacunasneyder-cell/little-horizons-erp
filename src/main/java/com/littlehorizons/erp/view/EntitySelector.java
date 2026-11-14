package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;

import java.util.List;
import java.util.function.Function;

/**
 * Reusable dialog: the user types part of a name or a document and picks one item from the
 * matches. It works for any entity because the search and the label are received as lambdas
 * (students, teachers...), so the same logic is not written twice.
 */
final class EntitySelector<T> {

    private static final int MAX_RESULTS = 15;

    private final ConsoleInput input;
    private final ConsoleOutput output;
    private final String prompt;
    private final Function<String, Result<List<T>>> search;
    private final Function<T, String> label;

    EntitySelector(ConsoleInput input, ConsoleOutput output, String prompt,
                   Function<String, Result<List<T>>> search, Function<T, String> label) {
        this.input = input;
        this.output = output;
        this.prompt = prompt;
        this.search = search;
        this.label = label;
    }

    /** Asks until one item is chosen. The user can type "cancelar" to leave. */
    T select() {
        while (true) {
            String term = input.readRequiredText(prompt, 100);
            Result<List<T>> result = search.apply(term);
            if (!result.isSuccess()) {
                output.error(result.getMessage());
                continue;
            }
            List<T> found = result.getData();
            if (found.isEmpty()) {
                output.error("No se encontraron resultados con «" + term + "». Intente de nuevo o escriba cancelar.");
            } else if (found.size() > MAX_RESULTS) {
                output.error("Hay " + found.size() + " coincidencias. Escriba más datos para acotar la búsqueda.");
            } else if (found.size() == 1) {
                return found.get(0);
            } else {
                return input.readOption("Seleccione una opción: ", found, label);
            }
        }
    }
}
