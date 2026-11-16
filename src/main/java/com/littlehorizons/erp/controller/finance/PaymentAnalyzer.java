package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.dao.PaidInstallment;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Pure calculations (Streams and lambdas, no database, no console) over payments: the totals of
 * a history and the list of students in arrears.
 */
public final class PaymentAnalyzer {

    private static final Collator SPANISH = Collator.getInstance(Locale.forLanguageTag("es-CO"));

    private PaymentAnalyzer() {
    }

    /** Totals of the payments of one or several students: overall, count and per payment method. */
    public static PaymentHistory history(List<StudentPayments> students) {
        List<Payment> all = students.stream().flatMap(s -> s.payments().stream()).toList();
        Map<PaymentMethod, BigDecimal> byMethod = all.stream()
                .collect(Collectors.groupingBy(Payment::getMethod,
                        () -> new EnumMap<>(PaymentMethod.class),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        return new PaymentHistory(students, all.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                all.size(), byMethod);
    }

    /**
     * The students with at least one installment that was due more than one month before {@code today}
     * and is not fully paid. A student only owes the months from his enrollment month on. The value
     * of the installment is his personalized value if he has one, the base value if not. The contact guardian is left null
     * (the controller adds it, it needs the database). The result
     * is ordered from the largest debt to the smallest (Spanish alphabetical order on ties).
     */
    public static List<DebtorEntry> debtors(List<Enrollment> enrollments, List<ConceptInstallments> concepts,
                                            LocalDate today) {
        int year = today.getYear();
        Map<Integer, Map<Integer, Map<Integer, BigDecimal>>> paid = concepts.stream().collect(Collectors.toMap(
                c -> c.concept().getId(), c -> c.paid().stream().collect(Collectors.groupingBy(
                        PaidInstallment::studentId, Collectors.toMap(PaidInstallment::month, PaidInstallment::total)))));
        return enrollments.stream()
                .map(e -> new DebtorEntry(e.getStudent(), null, concepts.stream()
                        .flatMap(c -> overdueOf(e, c, paid.get(c.concept().getId()), year, today).stream())
                        .toList()))
                .filter(d -> !d.installments().isEmpty())
                .sorted(Comparator.comparing(DebtorEntry::totalPending).reversed()
                        .thenComparing(d -> d.student().getFullName(), SPANISH))
                .toList();
    }

    private static List<OverdueInstallment> overdueOf(Enrollment enrollment, ConceptInstallments concept,
                                                      Map<Integer, Map<Integer, BigDecimal>> paidByStudent,
                                                      int year, LocalDate today) {
        int studentId = enrollment.getStudent().getId();
        BigDecimal price = concept.personalizedByStudent().getOrDefault(studentId, concept.concept().getBaseAmount());
        Map<Integer, BigDecimal> paidByMonth = paidByStudent.getOrDefault(studentId, Map.of());
        int firstMonth = enrollment.getEnrollmentDate().getYear() == year
                ? Math.max(PaymentSchedule.FIRST_MONTH, enrollment.getEnrollmentDate().getMonthValue())
                : PaymentSchedule.FIRST_MONTH;
        return IntStream.rangeClosed(firstMonth, PaymentSchedule.LAST_MONTH)
                .filter(month -> PaymentSchedule.isOverdue(year, month, today))
                .mapToObj(month -> new OverdueInstallment(concept.concept(), month, PaymentSchedule.dueDate(year, month),
                        price.subtract(paidByMonth.getOrDefault(month, BigDecimal.ZERO))))
                .filter(i -> i.pending().signum() > 0)
                .toList();
    }
}
