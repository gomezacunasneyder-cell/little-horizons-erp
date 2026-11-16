package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.GuardianController;
import com.littlehorizons.erp.controller.InvoiceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.finance.InvoiceDocument;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.util.Money;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * "Gestión Financiera - Facturas": issue an electronic invoice (a .txt file) for payments already made,
 * show an invoice, list the invoices of a student and generate a file again. It only asks and prints; the
 * rules (client, resolution, "a payment is invoiced once") live in the controller and the DAO.
 */
public final class InvoiceMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Emitir factura electrónica (genera el archivo TXT)",
            "Ver una factura por su número",
            "Facturas de un estudiante",
            "Generar de nuevo el archivo TXT de una factura");

    private final InvoiceController invoices;
    private final GuardianController guardians;
    private final StudentSelector studentSelector;
    private final ResultPrinter printer;

    public InvoiceMenuView(ConsoleInput input, ConsoleOutput output, InvoiceController invoices,
                           GuardianController guardians, StudentController students) {
        super(input, output);
        this.invoices = invoices;
        this.guardians = guardians;
        this.studentSelector = new StudentSelector(input, output, students);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "GESTIÓN FINANCIERA - FACTURAS ELECTRÓNICAS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> issueInvoice();
            case 2 -> showInvoice();
            case 3 -> studentInvoices();
            case 4 -> regenerate();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void issueInvoice() {
        Student student = studentSelector.select();
        Result<List<Payment>> pending = invoices.unbilledPayments(student);
        if (!pending.isSuccess()) {
            output.error(pending.getMessage());
            return;
        }
        List<Payment> payments = pending.getData();
        if (payments.isEmpty()) {
            output.info(student.getFullName() + " no tiene pagos pendientes de facturar.");
            return;
        }
        output.info("Pagos de " + student.getFullName() + " que aún no tienen factura:");
        output.table(List.of("N°", "Recibo", "Fecha", "Concepto", "Monto"),
                java.util.stream.IntStream.range(0, payments.size()).mapToObj(i -> List.of(String.valueOf(i + 1),
                        payments.get(i).getReceiptNumber(), payments.get(i).getPaymentDate().toString(),
                        label(payments.get(i)), Money.format(payments.get(i).getAmount()))).toList());
        List<Integer> positions = input.read("Números de los pagos a facturar (ejemplo: 1,3) o «todos»: ",
                text -> InvoiceSelection.parse(text, payments.size()));
        List<Payment> chosen = positions.stream().map(position -> payments.get(position - 1)).toList();

        Guardian client = readClient(student, chosen);
        if (client == null) {
            return;
        }
        if (!input.readYesNo("¿Emitir la factura de " + chosen.size() + " pago(s) a nombre de " + client.getFullName() + "?")) {
            output.info("No se emitió ninguna factura.");
            return;
        }
        Result<InvoiceDocument> result = invoices.issue(student, chosen, client);
        printer.report(result);
        if (result.isSuccess()) {
            printDocument(result.getData());
        }
    }

    /** The suggested client, unless the user prefers another guardian of the student. */
    private Guardian readClient(Student student, List<Payment> chosen) {
        Result<Guardian> suggested = invoices.suggestedClient(student, chosen);
        if (!suggested.isSuccess()) {
            output.error(suggested.getMessage());
            return null;
        }
        Guardian client = suggested.getData();
        if (input.readYesNo("Cliente sugerido: " + client.getFullName() + " (" + client.getDocumentType().getDbLabel() + " "
                + client.getDocumentNumber() + "). ¿Facturar a su nombre?")) {
            return client;
        }
        Result<List<StudentGuardian>> links = guardians.guardiansOf(student);
        if (!links.isSuccess()) {
            output.error(links.getMessage());
            return null;
        }
        return input.readOption("Acudiente a cuyo nombre se factura: ", links.getData(),
                link -> link.getGuardian().getFullName() + " (" + link.getGuardian().getRelationship() + ")").getGuardian();
    }

    private void showInvoice() {
        String number = input.read("Número de la factura (ejemplo: 15 o FE-000015): ", Invoice::normalizeNumber);
        Result<InvoiceDocument> result = invoices.view(number);
        if (result.isSuccess()) {
            printDocument(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void studentInvoices() {
        Student student = studentSelector.select();
        Result<List<Invoice>> result = invoices.invoicesOf(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else if (result.getData().isEmpty()) {
            output.info(student.getFullName() + " no tiene facturas.");
        } else {
            output.table(List.of("Factura", "Fecha", "Cliente", "Pagos", "Total", "Archivo"),
                    result.getData().stream().map(i -> List.of(i.getNumber(), i.getIssuedAt().toLocalDate().toString(),
                            i.getClient().getFullName(), String.valueOf(i.getLines().size()), Money.format(i.getTotal()),
                            i.getFileName())).toList());
        }
    }

    private void regenerate() {
        String number = input.read("Número de la factura (ejemplo: 15 o FE-000015): ", Invoice::normalizeNumber);
        printer.report(invoices.regenerateFile(number));
    }

    private void printDocument(InvoiceDocument document) {
        output.blank();
        document.text().forEach(output::line);
    }

    private static String label(Payment payment) {
        String name = payment.getConcept().getName();
        if (payment.getInstallmentMonth() == null) {
            return name + " " + payment.getAcademicYear();
        }
        return name + " - " + Month.of(payment.getInstallmentMonth()).getDisplayName(TextStyle.FULL,
                Locale.forLanguageTag("es-CO")) + " " + payment.getAcademicYear();
    }
}
