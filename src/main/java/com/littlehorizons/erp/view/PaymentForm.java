package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.finance.PaymentSchedule;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.enums.PaymentMethod;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

/** Asks the details of a payment, one answer at a time, until each is valid. The rules are in Payment and the controller. */
final class PaymentForm {

    private final ConsoleInput input;

    PaymentForm(ConsoleInput input) {
        this.input = input;
    }

    /** The school month of the installment, asked only for recurring concepts (tuition); null for the others. */
    Integer readMonth(PaymentConcept concept) {
        if (!concept.isRecurring()) {
            return null;
        }
        return input.readInt("Mes de la cuota, de " + PaymentSchedule.FIRST_MONTH + " (febrero) a "
                + PaymentSchedule.LAST_MONTH + " (noviembre): ", PaymentSchedule.FIRST_MONTH, PaymentSchedule.LAST_MONTH);
    }

    /** The amount in pesos, or null when the user presses Enter to pay everything that is pending. */
    BigDecimal readAmount() {
        return input.read("Monto en pesos, sin puntos (Enter = pagar todo lo pendiente): ",
                text -> text.isEmpty() ? null : PaymentConcept.checkAmount(Money.parse(text)));
    }

    LocalDate readDate() {
        return input.readDateOrDefault("Fecha del pago aaaa-mm-dd (Enter = hoy): ", LocalDate.now());
    }

    PaymentMethod readMethod() {
        return input.readOption("Método de pago: ", Arrays.asList(PaymentMethod.values()), PaymentMethod::getDisplayName);
    }
}
