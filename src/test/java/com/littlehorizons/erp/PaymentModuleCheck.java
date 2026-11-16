package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.DebtorController;
import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.StudentPriceController;
import com.littlehorizons.erp.controller.finance.ConceptInstallments;
import com.littlehorizons.erp.controller.finance.DebtorEntry;
import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.finance.OverdueInstallment;
import com.littlehorizons.erp.controller.finance.PaymentAnalyzer;
import com.littlehorizons.erp.controller.finance.PaymentBalance;
import com.littlehorizons.erp.controller.finance.PaymentHistory;
import com.littlehorizons.erp.controller.finance.PaymentSchedule;
import com.littlehorizons.erp.controller.finance.StudentPayments;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.PaidInstallment;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.PaymentMethod;
import com.littlehorizons.erp.util.ErrorLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Manual check of module 3.2 (plain Java). It creates its own students (documents starting with
 * 9970), guardians (9971) and concepts with the code ZZPRUEBA_..., and at the end it deletes them,
 * their payments and gives back the receipt numbers it used. The last checks read the sample data
 * of docs/database.sql (calculated at 2026-11-14). Run main() from IntelliJ with the database
 * loaded and config.txt in place.
 */
public final class PaymentModuleCheck {

    private static int failures = 0;
    private static final LocalDate SAMPLE_DAY = LocalDate.of(2026, 11, 14);

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        PaymentConceptController concepts = new PaymentConceptController(daos.paymentConceptDao(), logger);
        StudentPriceController prices = new StudentPriceController(daos.studentConceptPriceDao(), daos.paymentConceptDao(), logger);
        PaymentController payments = new PaymentController(daos.paymentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.enrollmentDao(), daos.studentDao(), logger);
        DebtorController debtors = new DebtorController(daos.enrollmentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.paymentDao(), daos.studentDao(), logger);
        int year = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();

        try {
            cleanUp();

            // ---- entities and the calendar ----
            check("The three payment methods match the database ENUM", () ->
                    PaymentMethod.values().length == 3 && PaymentMethod.CASH.getDbLabel().equals("Efectivo")
                            && PaymentMethod.TRANSFER.getDbLabel().equals("Transferencia")
                            && PaymentMethod.CARD.getDbLabel().equals("Tarjeta")
                            && PaymentMethod.fromDbLabel("Tarjeta") == PaymentMethod.CARD);
            Student probe = student("99700000009", "Probe");
            probe.setId(1);
            PaymentConcept pension = concept("ZZPRUEBA_A", "Pension Zzprueba", 600000, true);
            pension.setId(1);
            check("A payment needs a positive amount with two decimals, a date that is not in the future and a valid month", () ->
                    throwsValidation(() -> payment(probe, pension, "0", today, 3))
                            && throwsValidation(() -> payment(probe, pension, "-5", today, 3))
                            && throwsValidation(() -> payment(probe, pension, "10.123", today, 3))
                            && throwsValidation(() -> payment(probe, pension, "100", null, 3))
                            && throwsValidation(() -> payment(probe, pension, "100", today.plusDays(1), 3))
                            && throwsValidation(() -> payment(probe, pension, "100", LocalDate.of(1999, 1, 1), 3))
                            && throwsValidation(() -> payment(probe, pension, "100", today, 0))
                            && throwsValidation(() -> payment(probe, pension, "100", today, 13))
                            && payment(probe, pension, "100", today, null).getInstallmentMonth() == null
                            && payment(probe, pension, "100.50", today, 12).getAmount().equals(new BigDecimal("100.50"))
                            && payment(probe, pension, "100", today, 3).getReceiptNumber() == null);
            check("Installments run from February to November and are due on the 5th", () ->
                    PaymentSchedule.dueDate(2026, 10).equals(LocalDate.of(2026, 10, 5))
                            && PaymentSchedule.checkMonth(pension, 2) == 2 && PaymentSchedule.checkMonth(pension, 11) == 11
                            && throwsValidation(() -> PaymentSchedule.checkMonth(pension, 1))
                            && throwsValidation(() -> PaymentSchedule.checkMonth(pension, 12))
                            && throwsValidation(() -> PaymentSchedule.checkMonth(pension, null))
                            && PaymentSchedule.checkMonth(concept("ZZPRUEBA_B", "Uniforme Zzprueba", 100, false), null) == null
                            && throwsValidation(() -> PaymentSchedule.checkMonth(concept("ZZPRUEBA_B", "Uniforme Zzprueba", 100, false), 3)));
            check("An installment is in arrears only when MORE than one month passed after its due date", () ->
                    PaymentSchedule.isOverdue(2026, 10, LocalDate.of(2026, 11, 6))
                            && !PaymentSchedule.isOverdue(2026, 10, LocalDate.of(2026, 11, 5))
                            && !PaymentSchedule.isOverdue(2026, 10, LocalDate.of(2026, 10, 20))
                            && !PaymentSchedule.isOverdue(2026, 11, SAMPLE_DAY)
                            && PaymentSchedule.isOverdue(2026, 9, SAMPLE_DAY));

            // ---- pure calculations: who is in arrears ----
            check("The debtors calculation uses personalized values, partial payments, the enrollment month and sorts by debt", () -> {
                GradeLevel grade = new GradeLevel(1, "Primero", 1);
                Student a = studentWithId(101, "Alicia");
                Student b = studentWithId(102, "Beatriz");
                Student c = studentWithId(103, "Carlos");
                Student d = studentWithId(104, "Diana");
                Student e = studentWithId(105, "Esteban");
                List<Enrollment> enrollments = List.of(
                        new Enrollment(a, grade, 2026, LocalDate.of(2026, 1, 15)),
                        new Enrollment(b, grade, 2026, LocalDate.of(2026, 1, 15)),
                        new Enrollment(c, grade, 2026, LocalDate.of(2026, 8, 3)),   // enrolled in August: owes from August
                        new Enrollment(d, grade, 2026, LocalDate.of(2026, 1, 15)),
                        new Enrollment(e, grade, 2026, LocalDate.of(2026, 1, 15)));
                List<PaidInstallment> paid = new ArrayList<>();
                for (int month = 2; month <= 10; month++) {
                    paid.add(new PaidInstallment(104, month, new BigDecimal("600000")));      // Diana is up to date
                }
                for (int month = 2; month <= 8; month++) {
                    paid.add(new PaidInstallment(101, month, new BigDecimal("600000")));      // Alicia stopped paying after August
                }
                paid.add(new PaidInstallment(101, 9, new BigDecimal("250000")));              // and only paid part of September
                // Beatriz paid nothing but has a 50% scholarship; Esteban has a full scholarship (value zero)
                ConceptInstallments tuition = new ConceptInstallments(pension,
                        Map.of(102, new BigDecimal("300000"), 105, BigDecimal.ZERO), paid);
                List<DebtorEntry> result = PaymentAnalyzer.debtors(enrollments, List.of(tuition), SAMPLE_DAY);
                Map<Integer, DebtorEntry> byId = result.stream().collect(Collectors.toMap(x -> x.student().getId(), x -> x));
                return result.size() == 3 && byId.keySet().equals(Set.of(101, 102, 103))
                        && result.get(0).student().getId() == 102                          // 9 months x 300.000 = 2.700.000
                        && byId.get(102).installments().size() == 9
                        && byId.get(102).totalPending().compareTo(new BigDecimal("2700000")) == 0
                        && byId.get(101).installments().stream().map(OverdueInstallment::month).toList().equals(List.of(9, 10))
                        && byId.get(101).totalPending().compareTo(new BigDecimal("950000")) == 0   // 350.000 + 600.000
                        && byId.get(103).installments().stream().map(OverdueInstallment::month).toList().equals(List.of(8, 9, 10))
                        && byId.get(103).oldestDueDate().equals(LocalDate.of(2026, 8, 5))
                        && result.get(1).student().getId() == 103 && result.get(2).student().getId() == 101
                        && PaymentAnalyzer.debtors(enrollments, List.of(tuition), LocalDate.of(2026, 2, 20)).isEmpty();
            });
            check("The history totals are calculated per method with streams", () -> {
                Payment p1 = payment(probe, pension, "100000", today, 3);
                p1.setReceiptNumber("REC-X");
                Payment p2 = new Payment(probe, pension, null, new BigDecimal("50000"), today, PaymentMethod.CARD, year, 4);
                Payment p3 = new Payment(probe, pension, null, new BigDecimal("25000"), today, PaymentMethod.CARD, year, 5);
                PaymentHistory h = PaymentAnalyzer.history(List.of(new StudentPayments(probe, List.of(p1, p2, p3))));
                return h.paymentCount() == 3 && h.total().compareTo(new BigDecimal("175000")) == 0
                        && h.totalByMethod().get(PaymentMethod.CARD).compareTo(new BigDecimal("75000")) == 0
                        && h.totalByMethod().get(PaymentMethod.CASH).compareTo(new BigDecimal("100000")) == 0
                        && !h.totalByMethod().containsKey(PaymentMethod.TRANSFER)
                        && PaymentAnalyzer.history(List.of(new StudentPayments(probe, List.of()))).paymentCount() == 0;
            });

            // ---- data for the database checks ----
            PaymentConcept tuition = concept("ZZPRUEBA_CUOTA", "Cuota Zzprueba", 600000, true);
            PaymentConcept uniform = concept("ZZPRUEBA_UNIFORME", "Uniforme Zzprueba", 200000, false);
            PaymentConcept old = concept("ZZPRUEBA_VIEJO", "Concepto Zzprueba Viejo", 90000, false);
            PaymentConcept certificate = concept("ZZPRUEBA_CERT", "Certificado Zzprueba", 30000, false);
            concepts.create(certificate);
            concepts.create(tuition);
            concepts.create(uniform);
            concepts.create(old);
            List<Student> pupils = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Student s = student("9970000000" + i, "Pago" + "ABC".charAt(i));
                pupils.add(s);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "9971000000" + i, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
            }
            Student ana = pupils.get(0);
            Student bea = pupils.get(1);
            Student cami = pupils.get(2);
            Guardian anaMother = daos.studentDao().findGuardians(ana.getId()).get(0).getGuardian();
            Guardian cariMother = daos.studentDao().findGuardians(cami.getId()).get(0).getGuardian();
            Guardian beaMother = daos.studentDao().findGuardians(bea.getId()).get(0).getGuardian();
            daos.studentDao().addGuardian(bea.getId(), new StudentGuardian(anaMother, false, false)); // Ana and Bea are sisters
            execute("INSERT INTO matriculas (id_estudiante, id_grado, anio_academico, estado_matricula, fecha_matricula) "
                    + "SELECT id_estudiante, (SELECT MIN(id_grado) FROM grados), 2026, 'Activa', '2026-01-15' FROM estudiantes "
                    + "WHERE numero_documento IN ('99700000000', '99700000001')");
            concepts.setActive(old, false);
            old.setActive(false);
            PaymentConcept oldLoaded = daos.paymentConceptDao().findById(old.getId()).orElseThrow();
            int firstReceipt = (int) scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'");
            prices.assign(new StudentConceptPrice(bea, tuition, 2026, new BigDecimal("500000"), "Descuento por hermanos"));
            prices.assign(new StudentConceptPrice(cami, uniform, 2026, BigDecimal.ZERO, "Beca total"));

            // ---- registering payments ----
            check("The balance shows the value owed (base or personalized), what was paid and what is pending", () -> {
                PaymentBalance anaBalance = payments.balance(ana, tuition, 2026, 2).getData();
                PaymentBalance beaBalance = payments.balance(bea, tuition, 2026, 2).getData();
                return anaBalance.owed().compareTo(new BigDecimal("600000")) == 0 && anaBalance.paid().signum() == 0
                        && anaBalance.pending().compareTo(new BigDecimal("600000")) == 0
                        && beaBalance.owed().compareTo(new BigDecimal("500000")) == 0
                        && !payments.balance(ana, tuition, 2026, null).isSuccess()
                        && payments.balance(ana, uniform, 2026, null).getData().owed().compareTo(new BigDecimal("200000")) == 0;
            });
            Result<Payment> first = payments.register(ana, tuition, anaMother, null, LocalDate.of(2026, 2, 3),
                    PaymentMethod.TRANSFER, 2026, 2);
            check("Pay a tuition installment in full (Enter = everything pending): the system gives the receipt number", () ->
                    first.isSuccess() && first.getData().getId() != null
                            && first.getData().getReceiptNumber().equals(String.format("REC-%06d", firstReceipt + 1))
                            && first.getData().getAmount().compareTo(new BigDecimal("600000")) == 0
                            && first.getMessage().contains("REC-") && first.getMessage().contains("pagado por completo")
                            && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'") == firstReceipt + 1
                            && scalar("SELECT COUNT(*) FROM pagos WHERE numero_recibo = '" + first.getData().getReceiptNumber()
                            + "' AND metodo_pago = 'Transferencia' AND mes_cuota = 2 AND anio_academico = 2026 AND id_acudiente = "
                            + anaMother.getId()) == 1);
            check("A personalized value is charged instead of the base value", () -> {
                Result<Payment> r = payments.register(bea, tuition, null, null, LocalDate.of(2026, 2, 4), PaymentMethod.CASH, 2026, 2);
                return r.isSuccess() && r.getData().getAmount().compareTo(new BigDecimal("500000")) == 0
                        && r.getData().getReceiptNumber().equals(String.format("REC-%06d", firstReceipt + 2));
            });
            check("A paid installment cannot be paid again", () -> {
                Result<Payment> again = payments.register(ana, tuition, null, null, today, PaymentMethod.CASH, 2026, 2);
                Result<Payment> explicit = payments.register(ana, tuition, null, new BigDecimal("1000"), today, PaymentMethod.CASH, 2026, 2);
                return !again.isSuccess() && again.getMessage().contains("nada pendiente")
                        && !explicit.isSuccess() && explicit.getMessage().contains("superaría");
            });
            check("A payment in parts works until the installment is complete", () -> {
                Result<Payment> part1 = payments.register(ana, tuition, null, new BigDecimal("250000"), LocalDate.of(2026, 3, 2), PaymentMethod.CARD, 2026, 3);
                PaymentBalance mid = payments.balance(ana, tuition, 2026, 3).getData();
                Result<Payment> part2 = payments.register(ana, tuition, null, null, LocalDate.of(2026, 3, 20), PaymentMethod.CASH, 2026, 3);
                return part1.isSuccess() && part1.getMessage().contains("Quedan pendientes $350.000")
                        && mid.paid().compareTo(new BigDecimal("250000")) == 0 && mid.pending().compareTo(new BigDecimal("350000")) == 0
                        && part2.isSuccess() && part2.getData().getAmount().compareTo(new BigDecimal("350000")) == 0
                        && payments.balance(ana, tuition, 2026, 3).getData().pending().signum() == 0;
            });
            check("A payment above what is owed is refused and gives no receipt number and saves nothing", () -> {
                long receipts = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'");
                long rows = scalar("SELECT COUNT(*) FROM pagos");
                Result<Payment> r = payments.register(ana, tuition, null, new BigDecimal("600001"), today, PaymentMethod.CASH, 2026, 4);
                return !r.isSuccess() && r.getMessage().contains("superaría") && r.getMessage().contains("$600.000")
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'") == receipts
                        && scalar("SELECT COUNT(*) FROM pagos") == rows;
            });
            check("Tuition needs a month from February to November; other concepts must not have one", () ->
                    !payments.register(ana, tuition, null, null, today, PaymentMethod.CASH, 2026, null).isSuccess()
                            && !payments.register(ana, tuition, null, null, today, PaymentMethod.CASH, 2026, 1).isSuccess()
                            && !payments.register(ana, tuition, null, null, today, PaymentMethod.CASH, 2026, 12).isSuccess()
                            && !payments.register(ana, uniform, null, null, today, PaymentMethod.CASH, 2026, 5).isSuccess());
            check("A one-off concept is paid with its value and then it is complete", () -> {
                Result<Payment> r = payments.register(ana, uniform, null, null, LocalDate.of(2026, 1, 20), PaymentMethod.CARD, 2026, null);
                return r.isSuccess() && r.getData().getAmount().compareTo(new BigDecimal("200000")) == 0
                        && r.getData().getInstallmentMonth() == null
                        && !payments.register(ana, uniform, null, null, today, PaymentMethod.CASH, 2026, null).isSuccess();
            });
            check("A student with a full scholarship (value zero) has nothing to pay", () -> {
                Result<Payment> r = payments.register(cami, uniform, null, null, today, PaymentMethod.CASH, 2026, null);
                return !r.isSuccess() && r.getMessage().contains("nada pendiente")
                        && !payments.register(cami, uniform, null, new BigDecimal("1000"), today, PaymentMethod.CASH, 2026, null).isSuccess();
            });
            check("A concept that is inactive cannot be charged", () -> {
                Result<Payment> r = payments.register(ana, oldLoaded, null, new BigDecimal("1000"), today, PaymentMethod.CASH, 2026, null);
                return !r.isSuccess() && r.getMessage().contains("inactivo");
            });
            check("Tuition needs an active enrollment in that year; a one-off concept does not", () -> {
                Result<Payment> noEnrollment = payments.register(cami, tuition, null, null, today, PaymentMethod.CASH, 2026, 2);
                Result<Payment> other = payments.register(cami, tuition, null, null, today, PaymentMethod.CASH, 2025, 2);
                return !noEnrollment.isSuccess() && noEnrollment.getMessage().contains("matrícula activa")
                        && !other.isSuccess()
                        && payments.register(cami, certificate, null, null, today, PaymentMethod.CASH, 2026, null).isSuccess();
            });
            check("The payer must be a guardian of the student", () -> {
                Result<Payment> stranger = payments.register(ana, uniform, cariMother, new BigDecimal("1000"), today, PaymentMethod.CASH, 2026, null);
                Result<Payment> sibling = payments.register(bea, uniform, anaMother, new BigDecimal("50000"), today, PaymentMethod.CASH, 2026, null);
                return !stranger.isSuccess() && stranger.getMessage().contains("no es acudiente")
                        && sibling.isSuccess() && sibling.getData().getPayer().getId().equals(anaMother.getId());
            });
            check("The date cannot be in the future and the amount must be positive", () ->
                    !payments.register(bea, uniform, null, new BigDecimal("1000"), today.plusDays(1), PaymentMethod.CASH, 2026, null).isSuccess()
                            && !payments.register(bea, uniform, null, BigDecimal.ZERO, today, PaymentMethod.CASH, 2026, null).isSuccess());

            // ---- the receipt number under pressure ----
            check("Eight payments at the same moment get eight different consecutive receipt numbers", () -> {
                long before = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'");
                List<Integer> months = List.of(4, 5, 6, 7, 8, 9, 10, 11);
                ExecutorService pool = Executors.newFixedThreadPool(months.size());
                CyclicBarrier start = new CyclicBarrier(months.size());
                List<Future<Result<Payment>>> results = new ArrayList<>();
                for (int month : months) {
                    results.add(pool.submit(() -> {
                        start.await();
                        LocalDate when = LocalDate.of(2026, month, 6).isAfter(today) ? today : LocalDate.of(2026, month, 6);
                        return payments.register(ana, tuition, null, null, when, PaymentMethod.CASH, 2026, month);
                    }));
                }
                List<String> receipts = new ArrayList<>();
                for (Future<Result<Payment>> f : results) {
                    Result<Payment> r = f.get();
                    if (r.isSuccess()) {
                        receipts.add(r.getData().getReceiptNumber());
                    }
                }
                pool.shutdown();
                List<String> expected = new ArrayList<>();
                for (int i = 1; i <= months.size(); i++) {
                    expected.add(String.format("REC-%06d", before + i));
                }
                return receipts.stream().sorted().toList().equals(expected)
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Recibo'") == before + months.size();
            });
            check("Two payments of the full value of the same installment at the same moment: only one is accepted", () -> {
                long before = scalar("SELECT COUNT(*) FROM pagos WHERE id_estudiante = " + bea.getId() + " AND mes_cuota = 3");
                ExecutorService pool = Executors.newFixedThreadPool(2);
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return payments.register(bea, tuition, null, new BigDecimal("500000"), LocalDate.of(2026, 3, 9), PaymentMethod.CASH, 2026, 3).isSuccess();
                    }));
                }
                int accepted = 0;
                for (Future<Boolean> f : results) {
                    accepted += f.get() ? 1 : 0;
                }
                pool.shutdown();
                return accepted == 1 && scalar("SELECT COUNT(*) FROM pagos WHERE id_estudiante = " + bea.getId() + " AND mes_cuota = 3") == before + 1
                        && scalar("SELECT SUM(monto_pagado) FROM pagos WHERE id_estudiante = " + bea.getId() + " AND mes_cuota = 3") == 500000;
            });
            check("The receipt number is unique and the table rejects methods, amounts and months outside the rules", () ->
                    translatedFailure("INSERT INTO pagos (id_estudiante, id_concepto, monto_pagado, fecha_pago, metodo_pago, numero_recibo, anio_academico) "
                            + "SELECT id_estudiante, id_concepto, 1000, '2026-01-01', 'Efectivo', numero_recibo, 2026 FROM pagos WHERE numero_recibo = 'REC-"
                            + String.format("%06d", firstReceipt + 1) + "'")
                            && translatedFailure(rawPayment("'Cheque'", "1000", "NULL", "'ZZ-1'"))
                            && translatedFailure(rawPayment("'Efectivo'", "0", "NULL", "'ZZ-2'"))
                            && translatedFailure(rawPayment("'Efectivo'", "1000", "13", "'ZZ-3'"))
                            && translatedFailure(rawPayment("'Efectivo'", "1000", "NULL", "NULL"))
                            && !translatedFailure(rawPayment("'Tarjeta'", "1000", "NULL", "'ZZ-4'")));

            // ---- history ----
            check("The history of a student lists his payments newest first with the total", () -> {
                PaymentHistory h = payments.historyOfStudent(ana).getData();
                List<Payment> list = h.students().get(0).payments();
                boolean sorted = true;
                for (int i = 1; i < list.size(); i++) {
                    sorted &= !list.get(i - 1).getPaymentDate().isBefore(list.get(i).getPaymentDate());
                }
                return h.students().size() == 1 && h.paymentCount() == list.size() && list.size() >= 12 && sorted
                        && h.total().compareTo(list.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)) == 0
                        && list.stream().anyMatch(p -> p.getPayer() != null && p.getPayer().getId().equals(anaMother.getId()))
                        && list.stream().anyMatch(p -> p.getPayer() == null)
                        && payments.historyOfStudent(cami).getData().paymentCount() == 1          // only the certificate
                        && payments.historyOfStudent(cami).getData().total().compareTo(new BigDecimal("30000")) == 0;
            });
            check("The history of a guardian groups the payments of ALL his children", () -> {
                PaymentHistory h = payments.historyOfGuardian(anaMother).getData();
                Map<Integer, StudentPayments> byStudent = h.students().stream().collect(Collectors.toMap(s -> s.student().getId(), s -> s));
                return h.students().size() == 2 && byStudent.containsKey(ana.getId()) && byStudent.containsKey(bea.getId())
                        && h.paymentCount() == byStudent.get(ana.getId()).payments().size() + byStudent.get(bea.getId()).payments().size()
                        && h.total().compareTo(byStudent.get(ana.getId()).total().add(byStudent.get(bea.getId()).total())) == 0
                        && payments.historyOfGuardian(cariMother).getData().students().size() == 1;
            });

            // ---- debtors in the database ----
            check("A student who paid every installment is not in arrears; Bea paid only February and March, owes the discounted value and her contact is her own main guardian", () -> {
                DebtorReport report = debtors.report(SAMPLE_DAY).getData();
                Map<Integer, DebtorEntry> byId = report.debtors().stream().collect(Collectors.toMap(d -> d.student().getId(), d -> d));
                DebtorEntry beaEntry = byId.get(bea.getId());
                return monthsOf(byId.get(ana.getId()), tuition).isEmpty()
                        && monthsOf(beaEntry, tuition).equals(List.of(4, 5, 6, 7, 8, 9, 10))
                        && beaEntry.installments().stream().filter(i -> i.concept().getId().equals(tuition.getId()))
                        .map(OverdueInstallment::pending).allMatch(p -> p.compareTo(new BigDecimal("500000")) == 0)
                        && beaEntry.contact() != null && beaEntry.contact().getId().equals(beaMother.getId());
            });

            // ---- sample data ----
            check("Sample data: 500 payments with consecutive receipts and the counter at the last receipt", () ->
                    scalar("SELECT COUNT(*) FROM pagos WHERE numero_recibo LIKE 'REC-%'") >= 500
                            && scalar("SELECT COUNT(*) FROM pagos WHERE numero_recibo = 'REC-000001'") == 1
                            && scalar("SELECT COUNT(DISTINCT metodo_pago) FROM pagos") == 3);
            check("Sample data: the debtors at 2026-11-14 are exactly the students prepared for it", () -> {
                DebtorReport report = debtors.report(SAMPLE_DAY).getData();
                Map<Integer, DebtorEntry> sample = report.debtors().stream()
                        .filter(d -> !d.student().getDocumentNumber().startsWith("9970"))
                        .filter(d -> !monthsOf(d, null).isEmpty())   // the test concept ZZPRUEBA_CUOTA is also recurring
                        .collect(Collectors.toMap(d -> d.student().getId(), d -> d));
                return sample.keySet().equals(Set.of(4, 11, 19, 26, 29, 31))
                        && monthsOf(sample.get(4), null).equals(List.of(6, 7, 8, 9, 10))
                        && monthsOf(sample.get(26), null).equals(List.of(4, 5, 6, 7, 8, 9, 10))
                        && monthsOf(sample.get(11), null).equals(List.of(9, 10))
                        && monthsOf(sample.get(19), null).equals(List.of(9, 10))
                        && monthsOf(sample.get(31), null).equals(List.of(10))
                        && monthsOf(sample.get(29), null).equals(List.of(10))
                        && pensionPending(sample.get(19)).compareTo(priceOf(19).multiply(BigDecimal.valueOf(2)).subtract(new BigDecimal("300000"))) == 0
                        && pensionPending(sample.get(26)).compareTo(priceOf(26).multiply(BigDecimal.valueOf(7))) == 0;
            });
            check("Sample data: students who paid in two parts, have a full scholarship or only owe the current month are not in arrears", () -> {
                Set<Integer> ids = debtors.report(SAMPLE_DAY).getData().debtors().stream()
                        .filter(d -> !monthsOf(d, null).isEmpty()).map(d -> d.student().getId()).collect(Collectors.toSet());
                return !ids.contains(15) && !ids.contains(7) && !ids.contains(8) && !ids.contains(23)
                        && scalar("SELECT COUNT(*) FROM pagos WHERE id_estudiante = 15 AND mes_cuota = 7") == 2;
            });
            check("When nobody is in arrears the report says so (before the first installment is a month old)", () -> {
                Result<DebtorReport> r = debtors.report(LocalDate.of(2026, 2, 20));
                return r.isSuccess() && r.getData().debtors().isEmpty() && r.getMessage().contains("No hay estudiantes")
                        && r.getData().asOf().equals(LocalDate.of(2026, 2, 20));
            });
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static List<Integer> monthsOf(DebtorEntry entry, PaymentConcept onlyConcept) {
        if (entry == null) {
            return List.of();
        }
        return entry.installments().stream()
                .filter(i -> onlyConcept == null ? i.concept().getCode().equals("PENSION_MENSUAL")
                        : i.concept().getId().equals(onlyConcept.getId()))
                .map(OverdueInstallment::month).toList();
    }

    /** What the student owes of the school tuition only (the test concept is recurring too). */
    private static BigDecimal pensionPending(DebtorEntry entry) {
        return entry.installments().stream().filter(i -> i.concept().getCode().equals("PENSION_MENSUAL"))
                .map(OverdueInstallment::pending).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal priceOf(int studentId) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE((SELECT v.monto_ajustado FROM valores_concepto_estudiante v WHERE v.id_concepto = c.id_concepto "
                             + "AND v.id_estudiante = ? AND v.anio_academico = 2026), c.monto_base) FROM conceptos_pago c "
                             + "WHERE c.codigo_concepto = 'PENSION_MENSUAL'")) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static String rawPayment(String method, String amount, String month, String receipt) {
        return "INSERT INTO pagos (id_estudiante, id_concepto, monto_pagado, fecha_pago, metodo_pago, numero_recibo, anio_academico, mes_cuota) "
                + "SELECT (SELECT MIN(id_estudiante) FROM estudiantes), (SELECT MIN(id_concepto) FROM conceptos_pago), " + amount
                + ", '2026-01-01', " + method + ", " + receipt + ", 2026, " + month;
    }

    private static Payment payment(Student s, PaymentConcept c, String amount, LocalDate date, Integer month) {
        return new Payment(s, c, null, new BigDecimal(amount), date, PaymentMethod.CASH, 2026, month);
    }

    private static PaymentConcept concept(String code, String name, long amount, boolean recurring) {
        return new PaymentConcept(code, name, null, BigDecimal.valueOf(amount), 0, recurring);
    }

    private static Student student(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2012, 5, 20)).admissionDate(LocalDate.now().minusYears(1)).build();
    }

    private static Student studentWithId(int id, String firstName) {
        Student s = student("99700011" + id, firstName);
        s.setId(id);
        return s;
    }

    private static long scalar(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static boolean translatedFailure(String sql) {
        try {
            execute(sql);
            return false;
        } catch (SQLException | DaoException e) {
            return true;
        }
    }

    private static boolean throwsValidation(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ValidationException e) {
            return true;
        }
    }

    private static void check(String name, Callable<Boolean> test) {
        boolean passed;
        String detail = "";
        try {
            passed = test.call();
        } catch (Exception e) {
            passed = false;
            detail = " -> " + e;
        }
        System.out.println((passed ? "PASS  " : "FAIL  ") + name + detail);
        if (!passed) {
            failures++;
        }
    }

    /**
     * Deletes what this check created: the payments of its students (a payment blocks the deletion of its
     * student), the students and guardians, the concepts and the raw rows; then the receipt counter goes
     * back to the last receipt that still exists, as if the check had never run.
     */
    private static void cleanUp() throws SQLException, DaoException {
        execute("DELETE FROM pagos WHERE numero_recibo LIKE 'ZZ-%' OR id_estudiante IN "
                + "(SELECT id_estudiante FROM estudiantes WHERE numero_documento LIKE '9970%')");
        execute("DELETE FROM estudiantes WHERE numero_documento LIKE '9970%'");
        execute("DELETE FROM acudientes WHERE numero_documento LIKE '9971%'");
        execute("DELETE FROM pagos WHERE id_concepto IN (SELECT id_concepto FROM conceptos_pago WHERE codigo_concepto LIKE 'ZZPRUEBA%')");
        execute("DELETE FROM conceptos_pago WHERE codigo_concepto LIKE 'ZZPRUEBA%'");
        execute("UPDATE consecutivos SET ultimo_numero = (SELECT COALESCE(MAX(CAST(SUBSTRING(numero_recibo, 5) AS UNSIGNED)), 0) "
                + "FROM pagos WHERE numero_recibo LIKE 'REC-%') WHERE tipo = 'Recibo'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
