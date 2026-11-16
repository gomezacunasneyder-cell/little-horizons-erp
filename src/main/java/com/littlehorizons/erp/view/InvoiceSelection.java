package com.littlehorizons.erp.view;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Invoice;

import java.util.ArrayList;
import java.util.List;

/** Turns what the user types ("1,3" or "todos") into the positions of the payments to invoice. */
public final class InvoiceSelection {

    private InvoiceSelection() {
    }

    /** Positions from 1 to {@code size}, without repeating, in the order typed; "todos" means all of them. */
    public static List<Integer> parse(String text, int size) {
        String clean = text == null ? "" : text.trim().toLowerCase();
        List<Integer> chosen = new ArrayList<>();
        if (clean.equals("todos")) {
            for (int i = 1; i <= Math.min(size, Invoice.MAX_LINES); i++) {
                chosen.add(i);
            }
            if (size > Invoice.MAX_LINES) {
                throw new ValidationException("Una factura admite máximo " + Invoice.MAX_LINES
                        + " pagos: escriba los números de los que quiere incluir.");
            }
            return chosen;
        }
        if (!clean.matches("\\d+(\\s*,\\s*\\d+)*")) {
            throw new ValidationException("Escriba los números separados por comas (ejemplo: 1,3) o la palabra «todos».");
        }
        for (String part : clean.split("\\s*,\\s*")) {
            int position = Integer.parseInt(part);
            if (position < 1 || position > size) {
                throw new ValidationException("El número " + part + " no está en la lista (1 a " + size + ").");
            }
            if (chosen.contains(position)) {
                throw new ValidationException("El número " + position + " está repetido.");
            }
            chosen.add(position);
        }
        if (chosen.size() > Invoice.MAX_LINES) {
            throw new ValidationException("Una factura admite máximo " + Invoice.MAX_LINES + " pagos.");
        }
        return chosen;
    }
}
