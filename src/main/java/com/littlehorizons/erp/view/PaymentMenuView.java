package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.DebtorController;
import com.littlehorizons.erp.controller.GuardianController;
import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.finance.PaymentBalance;
import com.littlehorizons.erp.controller.finance.PaymentHistory;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.PaymentMethod;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * "Gestión Financiera - Pagos": register a payment (the receipt number is given by the system),
 * the payment history of a student or of a guardian, and the report of students in arrears. It only
 * asks and prints; the rules are in the controllers, the entities and the DAO.
 */
public final class PaymentMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar un pago",
            "Historial de pagos de un estudiante",
            "Historial de pagos de un acudiente (todos sus hijos)",
            "Reporte de estudiantes morosos (más de un mes pendiente)");

    private final PaymentController payments;
    private final DebtorController debtors;
    private final GuardianController guardians;
    private final StudentSelector studentSelector;
    private final ConceptSelector conceptSelector;
    private final PaymentForm form;
    private final PaymentPrinter paymentPrinter;
    private final ResultPrinter printer;

    public PaymentMenuView(ConsoleInput input, ConsoleOutput output, PaymentController payments, DebtorController debtors,
                           GuardianController guardians, PaymentConceptController concepts, StudentController students) {
        super(input, output);
        this.payments = payments;
        this.debtors = debtors;
        this.guardians = guardians;
        this.studentSelector = new StudentSelector(input, output, students);
        this.conceptSelector = new ConceptSelector(input, output, concepts::searchActive);
        this.form = new PaymentForm(input);
        this.paymentPrinter = new PaymentPrinter(output);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "GESTIÓN FINANCIERA - PAGOS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> registerPayment();
            case 2 -> studentHistory();
            case 3 -> guardianHistory();
            case 4 -> debtorsReport();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void registerPayment() {
        Student student = studentSelector.select();
        PaymentConcept concept = conceptSelector.select();
        int year = input.readAcademicYear();
        Integer month = form.readMonth(concept);
        Result<PaymentBalance> balance = payments.balance(student, concept, year, month);
        if (!balance.isSuccess()) {
            output.error(balance.getMessage());
            return;
        }
        output.info("Valor que le corresponde: " + Money.format(balance.getData().owed()) + " | Ya pagado: "
                + Money.format(balance.getData().paid()) + " | Pendiente: " + Money.format(balance.getData().pending()));
        BigDecimal amount = form.readAmount();
        LocalDate date = form.readDate();
        PaymentMethod method = form.readMethod();
        Guardian payer = readPayer(student);
        if (!input.readYesNo("¿Registrar el pago de " + (amount == null ? Money.format(balance.getData().pending())
                : Money.format(amount)) + " de " + student.getFullName() + " por «" + concept.getName() + "»?")) {
            output.info("No se registró ningún pago.");
            return;
        }
        Result<Payment> result = payments.register(student, concept, payer, amount, date, method, year, month);
        printer.report(result);
        if (result.isSuccess()) {
            paymentPrinter.receipt(result.getData());
        }
    }

    /** Who paid: one of the guardians of the student, or nobody (null) when the user does not want to record it. */
    private Guardian readPayer(Student student) {
        Result<List<StudentGuardian>> links = guardians.guardiansOf(student);
        if (!links.isSuccess() || links.getData().isEmpty() || !input.readYesNo("¿Registrar qué acudiente pagó?")) {
            return null;
        }
        return input.readOption("Acudiente que pagó: ", links.getData(),
                link -> link.getGuardian().getFullName() + " (" + link.getGuardian().getRelationship() + ")").getGuardian();
    }

    private void studentHistory() {
        showHistory(payments.historyOfStudent(studentSelector.select()));
    }

    private void guardianHistory() {
        String document = input.readRequiredText("Documento del acudiente: ", 20);
        Result<Optional<Guardian>> found = guardians.findGuardian(document);
        if (!found.isSuccess()) {
            output.error(found.getMessage());
        } else if (found.getData().isEmpty()) {
            output.error("No hay un acudiente con el documento " + document + ".");
        } else {
            output.info("Acudiente: " + found.getData().get().getFullName());
            showHistory(payments.historyOfGuardian(found.getData().get()));
        }
    }

    private void showHistory(Result<PaymentHistory> result) {
        if (result.isSuccess()) {
            paymentPrinter.history(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void debtorsReport() {
        Result<DebtorReport> result = debtors.report(LocalDate.now());
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else if (result.getData().debtors().isEmpty()) {
            output.info(result.getMessage());
        } else {
            paymentPrinter.debtors(result.getData());
        }
    }
}
