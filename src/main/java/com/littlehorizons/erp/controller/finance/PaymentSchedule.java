package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.time.LocalDate;

/**
 * The calendar of the monthly installments (tuition). It is the only place that knows the school
 * months and the due day, so the payments and the debtors report always agree:
 * installments go from February to November, each one is due on day 5 of its month, and it is
 * considered in arrears when MORE than one month has passed after the due date.
 */
public final class PaymentSchedule {

    public static final int FIRST_MONTH = 2;
    public static final int LAST_MONTH = 11;
    public static final int DUE_DAY = 5;
    public static final int GRACE_MONTHS = 1;

    private PaymentSchedule() {
    }

    public static LocalDate dueDate(int year, int month) {
        return LocalDate.of(year, month, DUE_DAY);
    }

    /** True when the installment was due more than one month before {@code today} (pending for more than a month). */
    public static boolean isOverdue(int year, int month, LocalDate today) {
        return dueDate(year, month).plusMonths(GRACE_MONTHS).isBefore(today);
    }

    /**
     * A concept charged by installments (recurring) needs a school month; any other concept must not have one.
     * Returns the month to save.
     */
    public static Integer checkMonth(PaymentConcept concept, Integer month) {
        if (!concept.isRecurring()) {
            if (month != null) {
                throw new ValidationException("«" + concept.getName() + "» no se paga por cuotas mensuales.");
            }
            return null;
        }
        if (month == null || month < FIRST_MONTH || month > LAST_MONTH) {
            throw new ValidationException("La cuota de «" + concept.getName() + "» debe ser un mes entre "
                    + FIRST_MONTH + " (febrero) y " + LAST_MONTH + " (noviembre).");
        }
        return month;
    }
}
