package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.BillingProfile;
import com.littlehorizons.erp.controller.finance.CufeGenerator;
import com.littlehorizons.erp.controller.finance.InvoiceCalculator;
import com.littlehorizons.erp.controller.finance.InvoiceDocument;
import com.littlehorizons.erp.controller.finance.InvoiceFileStore;
import com.littlehorizons.erp.controller.finance.InvoiceTextRenderer;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.InvoiceDao;
import com.littlehorizons.erp.model.dao.PaymentDao;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.InvoiceLine;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.Money;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Issues electronic invoices (simulated) for payments that were already made, and answers them.
 * The invoice is saved in the database (with the next consecutive number) and then written as a .txt
 * file. If the file cannot be written the invoice still exists, and the file can be generated again.
 * The clock, the school data and the folder are received as Suppliers: the tests can issue invoices on a fixed date,
 * and config.txt is read when an invoice is made (the same way the grading strategy is read).
 */
public class InvoiceController extends BaseController {

    private final InvoiceDao invoiceDao;
    private final PaymentDao paymentDao;
    private final StudentDao studentDao;
    private final Supplier<BillingProfile> profileSource;
    private final Supplier<InvoiceFileStore> filesSource;
    private final Supplier<LocalDateTime> clock;
    private final ErrorLogger logger;

    public InvoiceController(InvoiceDao invoiceDao, PaymentDao paymentDao, StudentDao studentDao, Supplier<BillingProfile> profileSource,
                             Supplier<InvoiceFileStore> filesSource, Supplier<LocalDateTime> clock, ErrorLogger logger) {
        super(logger);
        this.invoiceDao = invoiceDao;
        this.paymentDao = paymentDao;
        this.studentDao = studentDao;
        this.profileSource = profileSource;
        this.filesSource = filesSource;
        this.clock = clock;
        this.logger = logger;
    }

    /** The payments of the student that are not in any invoice, oldest first. */
    public Result<List<Payment>> unbilledPayments(Student student) {
        return execute("List payments pending invoicing", () -> Result.ok(paymentDao.findUnbilledByStudent(student), ""));
    }

    /**
     * Who the invoice should be issued to by default: the guardian who paid (when every chosen payment was paid
     * by the same one), otherwise the financially responsible guardian (the main one first), otherwise the main guardian.
     */
    public Result<Guardian> suggestedClient(Student student, List<Payment> payments) {
        return execute("Suggest invoice client", () -> {
            List<Guardian> payers = payments.stream().map(Payment::getPayer).filter(Objects::nonNull)
                    .collect(Collectors.toMap(Guardian::getId, Function.identity(), (a, b) -> a)).values().stream().toList();
            boolean everyoneRecorded = payments.stream().allMatch(p -> p.getPayer() != null);
            if (everyoneRecorded && payers.size() == 1) {
                return Result.ok(payers.get(0), "");
            }
            List<StudentGuardian> links = studentDao.findGuardians(student.getId());
            Guardian suggested = links.stream()
                    .filter(l -> l.getGuardian().isFinanciallyResponsible())
                    .max(Comparator.comparing(StudentGuardian::isPrincipal))
                    .or(() -> links.stream().filter(StudentGuardian::isPrincipal).findFirst())
                    .map(StudentGuardian::getGuardian)
                    .orElseThrow(() -> new ValidationException(student.getFullName() + " no tiene acudientes registrados."));
            return Result.ok(suggested, "");
        });
    }

    /**
     * The invoice of a payment that was just confirmed: it goes to the guardian who paid, or to the financially
     * responsible guardian when the payer was not recorded. The payment stays valid if this fails (for example,
     * the client has no address): the invoice can be issued later from the invoices menu.
     */
    public Result<InvoiceDocument> issueForPayment(Payment payment) {
        Result<Guardian> client = suggestedClient(payment.getStudent(), List.of(payment));
        if (!client.isSuccess()) {
            return Result.fail(client.getMessage());
        }
        return issue(payment.getStudent(), List.of(payment), client.getData());
    }

    /** Issues one invoice with the chosen payments of the student, to the chosen guardian, and writes its .txt file. */
    public Result<InvoiceDocument> issue(Student student, List<Payment> chosen, Guardian client) {
        return execute("Issue invoice", () -> {
            if (chosen == null || chosen.isEmpty()) {
                throw new ValidationException("Elija al menos un pago para facturar.");
            }
            LocalDateTime now = clock.get();
            requireValidResolution(now.toLocalDate());
            requireBillableClient(student, client);
            // the payments are read again: only payments of THIS student that are still not invoiced are accepted
            Map<Integer, Payment> available = paymentDao.findUnbilledByStudent(student).stream()
                    .collect(Collectors.toMap(Payment::getId, Function.identity()));
            List<InvoiceLine> lines = chosen.stream().map(p -> {
                Payment current = available.get(p.getId());
                if (current == null) {
                    throw new ValidationException("El pago con recibo " + p.getReceiptNumber() + " no es de "
                            + student.getFullName() + " o ya fue facturado.");
                }
                return InvoiceCalculator.lineOf(current);
            }).toList();
            Invoice invoice = new Invoice(student, client, now, lines);
            invoiceDao.create(invoice, this::seal);
            return saved(invoice, "Factura " + invoice.getNumber() + " emitida por " + Money.format(invoice.getTotal())
                    + " a nombre de " + client.getFullName() + ".", true);
        });
    }

    /** The invoice with that number, with its text (the file is not touched). */
    public Result<InvoiceDocument> view(String number) {
        return execute("Show invoice", () -> {
            Invoice invoice = find(number);
            return Result.ok(new InvoiceDocument(invoice, InvoiceTextRenderer.render(invoice, profile()), files().pathOf(invoice)), "");
        });
    }

    /** The invoices of a student, newest first. */
    public Result<List<Invoice>> invoicesOf(Student student) {
        return execute("List student invoices", () -> Result.ok(invoiceDao.findByStudent(student), ""));
    }

    /** Writes the .txt file of an existing invoice again (same content: it is built from the saved data). */
    public Result<InvoiceDocument> regenerateFile(String number) {
        return execute("Regenerate invoice file", () -> {
            Invoice invoice = find(number);
            return saved(invoice, "Archivo de la factura " + invoice.getNumber() + " generado de nuevo.", false);
        });
    }

    /** Read at the moment of use, so a wrong billing key in config.txt is reported as an error of the operation, not at startup. */
    private BillingProfile profile() {
        return profileSource.get();
    }

    private InvoiceFileStore files() {
        return filesSource.get();
    }

    /** The number is already assigned here: the CUFE and the resolution depend on it. */
    private void seal(Invoice invoice) {
        if (invoice.getSequence() < profile().rangeFrom() || invoice.getSequence() > profile().rangeTo()) {
            throw new ValidationException("El rango de numeración autorizado por la resolución ("
                    + Invoice.numberOf(profile().rangeFrom()) + " a " + Invoice.numberOf(profile().rangeTo())
                    + ") no incluye el número " + invoice.getNumber() + ". Solicite una nueva resolución.");
        }
        invoice.setCufe(CufeGenerator.generate(invoice, profile()));
        invoice.setResolution(profile().resolutionText());
    }

    private void requireValidResolution(LocalDate today) {
        if (today.isBefore(profile().resolutionDate())) {
            throw new ValidationException("La resolución de facturación empieza el " + profile().resolutionDate()
                    + ": todavía no se pueden emitir facturas.");
        }
        if (today.isAfter(profile().resolutionValidUntil())) {
            throw new ValidationException("La resolución de facturación venció el " + profile().resolutionValidUntil()
                    + ". Solicite una nueva resolución para emitir facturas.");
        }
    }

    private void requireBillableClient(Student student, Guardian client) throws DaoException {
        if (client == null) {
            throw new ValidationException("Elija el acudiente a cuyo nombre se factura.");
        }
        if (studentDao.findGuardians(student.getId()).stream()
                .noneMatch(link -> link.getGuardian().getId().equals(client.getId()))) {
            throw new ValidationException(client.getFullName() + " no es acudiente de " + student.getFullName() + ".");
        }
        if (client.getAddress() == null || client.getAddress().isBlank()) {
            throw new ValidationException(client.getFullName() + " no tiene dirección registrada y la factura la exige. "
                    + "Actualícela en Estudiantes > Acudientes.");
        }
    }

    private Invoice find(String number) throws DaoException {
        Optional<Invoice> invoice = invoiceDao.findByNumber(Invoice.normalizeNumber(number));
        return invoice.orElseThrow(() -> new ValidationException("No existe la factura " + Invoice.normalizeNumber(number) + "."));
    }

    /**
     * Writes the file of a saved invoice. When the invoice was just issued ({@code justIssued}) a failure to write
     * is not an error of the operation: the invoice exists, and the user is told how to get the file later.
     */
    private Result<InvoiceDocument> saved(Invoice invoice, String message, boolean justIssued) {
        List<String> text = InvoiceTextRenderer.render(invoice, profile());
        try {
            Path file = files().write(invoice, text);
            return Result.ok(new InvoiceDocument(invoice, text, file), message + " Archivo: " + file);
        } catch (IOException e) {
            logger.error("Write invoice file " + invoice.getFileName() + " failed: " + e.getMessage(), e);
            String problem = "No se pudo guardar el archivo " + invoice.getFileName() + " (" + e.getMessage() + ").";
            if (!justIssued) {
                return Result.fail(problem);
            }
            return Result.ok(new InvoiceDocument(invoice, text, null), message + " " + problem
                    + " La factura quedó registrada: genere el archivo de nuevo cuando se corrija el problema.");
        }
    }
}
