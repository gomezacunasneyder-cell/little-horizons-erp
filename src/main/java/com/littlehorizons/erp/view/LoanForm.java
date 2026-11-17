package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.entity.Loan;

import java.time.LocalDate;

/** Asks for the data of a loan and of a return. It only reads; the dates are validated by the Loan entity. */
final class LoanForm {

    private final ConsoleInput input;

    LoanForm(ConsoleInput input) {
        this.input = input;
    }

    /** Return date of the loan; Enter accepts the proposed one. The Loan entity checks the range when it is registered. */
    LocalDate readDueDate(LocalDate proposed) {
        return input.read("Fecha de devolución programada (aaaa-mm-dd, Enter = " + proposed + "): ",
                text -> text.isEmpty() ? proposed : LocalDate.parse(text));
    }

    /** Optional (null when the user presses Enter). */
    String readObservations(String prompt) {
        return input.read(prompt, text -> Loan.checkObservations(text));
    }
}
