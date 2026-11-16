package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.finance.DebtorEntry;
import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.finance.OverdueInstallment;
import com.littlehorizons.erp.controller.finance.PaymentHistory;
import com.littlehorizons.erp.controller.finance.StudentPayments;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.util.Money;

import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Prints receipts, payment histories and the debtors report. It only formats; every number comes already calculated. */
final class PaymentPrinter {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private final ConsoleOutput output;

    PaymentPrinter(ConsoleOutput output) {
        this.output = output;
    }

    /** The receipt of a payment that was just saved. */
    void receipt(Payment payment) {
        output.blank();
        output.line("  RECIBO DE PAGO " + payment.getReceiptNumber());
        output.line("  Estudiante: " + payment.getStudent().getFullName());
        output.line("  Concepto:   " + conceptLabel(payment));
        output.line("  Monto:      " + Money.format(payment.getAmount()));
        output.line("  Fecha:      " + payment.getPaymentDate());
        output.line("  Método:     " + payment.getMethod().getDisplayName());
        output.line("  Pagó:       " + (payment.getPayer() == null ? "(no registrado)" : payment.getPayer().getFullName()));
    }

    void history(PaymentHistory history) {
        for (StudentPayments part : history.students()) {
            output.info("Pagos de " + part.student().getFullName() + " (" + part.payments().size() + "), total "
                    + Money.format(part.total()));
            output.table(List.of("Recibo", "Fecha", "Concepto", "Año", "Monto", "Método", "Pagó"),
                    part.payments().stream().map(this::historyRow).toList());
        }
        if (history.paymentCount() > 0) {
            output.info("TOTAL: " + history.paymentCount() + " pago(s) por " + Money.format(history.total()) + " ("
                    + history.totalByMethod().entrySet().stream()
                    .map(e -> e.getKey().getDisplayName() + " " + Money.format(e.getValue()))
                    .collect(Collectors.joining(", ")) + ").");
        }
    }

    private List<String> historyRow(Payment p) {
        return List.of(p.getReceiptNumber(), p.getPaymentDate().toString(), conceptLabel(p),
                String.valueOf(p.getAcademicYear()), Money.format(p.getAmount()), p.getMethod().getDisplayName(),
                p.getPayer() == null ? "-" : p.getPayer().getFullName());
    }

    void debtors(DebtorReport report) {
        output.info("Estudiantes con cuotas pendientes por más de un mes, al " + report.asOf());
        output.table(List.of("Estudiante", "Documento", "Cuotas vencidas", "Debe", "Vence desde", "Días", "Acudiente a contactar"),
                report.debtors().stream().map(d -> debtorRow(d, report)).toList());
        output.info(report.debtors().size() + " estudiante(s) en mora, " + report.overdueInstallments()
                + " cuota(s), total pendiente " + Money.format(report.totalPending()) + ".");
    }

    private List<String> debtorRow(DebtorEntry d, DebtorReport report) {
        return List.of(d.student().getFullName(), d.student().getDocumentNumber(),
                d.installments().stream().map(this::installmentLabel).collect(Collectors.joining(", ")),
                Money.format(d.totalPending()), d.oldestDueDate().toString(),
                String.valueOf(ChronoUnit.DAYS.between(d.oldestDueDate(), report.asOf())), contact(d.contact()));
    }

    private static String contact(Guardian guardian) {
        return guardian == null ? "(sin acudiente)" : guardian.getFullName() + " " + guardian.getPhone();
    }

    private String installmentLabel(OverdueInstallment i) {
        return Month.of(i.month()).getDisplayName(TextStyle.SHORT, SPANISH);
    }

    private static String conceptLabel(Payment p) {
        return p.getInstallmentMonth() == null ? p.getConcept().getName()
                : p.getConcept().getName() + " " + Month.of(p.getInstallmentMonth()).getDisplayName(TextStyle.SHORT, SPANISH);
    }
}
