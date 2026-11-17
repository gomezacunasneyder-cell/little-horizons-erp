package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.LoanController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.loan.LoanAnalyzer;
import com.littlehorizons.erp.controller.loan.LoanListing;
import com.littlehorizons.erp.controller.loan.OverdueReport;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Borrower;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.LoanStatus;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.EnumCounts;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual check of module 4.2 (plain Java). It creates its own resources with codes that start with
 * ZZP-L and deletes everything at the end (their loans go with them). The clock of the controller is
 * fixed, so the results do not depend on the day it runs; it never moves past 2026-11-18, so the
 * loans of docs/database.sql are never changed. Run main() from IntelliJ with the database loaded
 * and config.txt in place.
 */
public final class LoanModuleCheck {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        ResourceController resources = new ResourceController(daos.resourceDao(), logger);
        AtomicReference<LocalDate> clock = new AtomicReference<>(DAY);
        LoanController loans = new LoanController(daos.loanDao(), daos.resourceDao(), logger, clock::get);

        Borrower ana = Borrower.of(daos.studentDao().findById(1).orElseThrow());
        Borrower teacher1 = Borrower.of(findTeacher(daos, 1));
        Borrower graduated = Borrower.of(daos.studentDao().findById(34).orElseThrow());
        Borrower onLeave = Borrower.of(findTeacher(daos, 29));

        try {
            cleanUp();

            // ---- the enums and the borrower ----
            check("The enum labels are exactly the ones of the database columns; Activo and Vencido are open, Devuelto is not", () ->
                    List.of(LoanStatus.values()).stream().map(LoanStatus::getDbLabel).toList().equals(List.of("Activo", "Devuelto", "Vencido"))
                            && LoanStatus.ACTIVE.isOpen() && LoanStatus.OVERDUE.isOpen() && !LoanStatus.RETURNED.isOpen()
                            && List.of(BorrowerType.values()).stream().map(BorrowerType::getDbLabel).toList().equals(List.of("Estudiante", "Profesor"))
                            && LoanStatus.fromDbLabel("Vencido") == LoanStatus.OVERDUE
                            && BorrowerType.fromDbLabel("Profesor") == BorrowerType.TEACHER
                            && throwsIllegalArgument(() -> LoanStatus.fromDbLabel("Perdido")));
            check("Only active students and active teachers may borrow (Graduado, Retirado, Suspendido, Inactivo and Licencia may not)", () ->
                    Borrower.student(1, "A B", "1", null, null, StudentStatus.ACTIVE).active()
                            && !Borrower.student(1, "A B", "1", null, null, StudentStatus.GRADUATED).active()
                            && !Borrower.student(1, "A B", "1", null, null, StudentStatus.WITHDRAWN).active()
                            && !Borrower.student(1, "A B", "1", null, null, StudentStatus.SUSPENDED).active()
                            && Borrower.teacher(1, "A B", "1", null, null, TeacherStatus.ACTIVE).active()
                            && !Borrower.teacher(1, "A B", "1", null, null, TeacherStatus.INACTIVE).active()
                            && !Borrower.teacher(1, "A B", "1", null, null, TeacherStatus.ON_LEAVE).active());
            check("The label shows the state only when the person is not active; the description names the kind", () ->
                    !ana.label().contains("[") && graduated.label().contains("[Graduado]") && onLeave.label().contains("[En licencia]")
                            && ana.describe().startsWith("Estudiante ") && teacher1.describe().startsWith("Profesor "));

            check("The contact is the e-mail, or the phone when there is no e-mail, or nothing", () ->
                    "a@b.co".equals(Borrower.student(1, "A B", "1", "a@b.co", "3001234567", StudentStatus.ACTIVE).contact())
                            && "3001234567".equals(Borrower.student(1, "A B", "1", null, "3001234567", StudentStatus.ACTIVE).contact())
                            && "3001234567".equals(Borrower.teacher(1, "A B", "1", "  ", "3001234567", TeacherStatus.ACTIVE).contact())
                            && Borrower.student(1, "A B", "1", null, null, StudentStatus.ACTIVE).contact() == null);

            // ---- the Loan entity ----
            Resource sample = savedResource(resources, "ZZP-L00");
            check("A new loan starts Activo with no return date; the return date may equal the loan date or be 90 days later", () -> {
                Loan loan = Loan.lend(sample, ana, DAY, DAY.plusDays(7), null);
                return loan.getStatus() == LoanStatus.ACTIVE && loan.getReturnDate() == null && loan.getId() == null
                        && loan.getLoanDate().equals(DAY) && loan.getDueDate().equals(DAY.plusDays(7))
                        && Loan.lend(sample, ana, DAY, DAY, null).getDueDate().equals(DAY)
                        && Loan.lend(sample, ana, DAY, DAY.plusDays(90), null).getDueDate().equals(DAY.plusDays(90));
            });
            check("The return date is required, cannot be before the loan date and cannot be more than 90 days later", () ->
                    throwsValidation(() -> Loan.lend(sample, ana, DAY, null, null))
                            && throwsValidation(() -> Loan.lend(sample, ana, DAY, DAY.minusDays(1), null))
                            && throwsValidation(() -> Loan.lend(sample, ana, DAY, DAY.plusDays(91), null)));
            check("A person who is not active cannot borrow, and the message says why", () -> {
                try {
                    Loan.lend(sample, graduated, DAY, DAY.plusDays(7), null);
                    return false;
                } catch (ValidationException e) {
                    return e.getMessage().contains("Graduado") && e.getMessage().contains("activos");
                }
            });
            check("The resource (saved) and the borrower are required", () ->
                    throwsValidation(() -> Loan.lend(null, ana, DAY, DAY.plusDays(7), null))
                            && throwsValidation(() -> Loan.lend(new Resource("ZZP-L99", "Sin guardar", ResourceCategory.BOOK, null, null, "Biblioteca"),
                            ana, DAY, DAY.plusDays(7), null))
                            && throwsValidation(() -> Loan.lend(sample, null, DAY, DAY.plusDays(7), null)));
            check("The observations are optional (blank = none), trimmed and at most 500 characters", () ->
                    Loan.lend(sample, ana, DAY, DAY, "   ").getObservations() == null
                            && Loan.lend(sample, ana, DAY, DAY, "  Con funda ").getObservations().equals("Con funda")
                            && throwsValidation(() -> Loan.lend(sample, ana, DAY, DAY, "x".repeat(501)))
                            && Loan.lend(sample, ana, DAY, DAY, "x".repeat(500)).getObservations().length() == 500);
            check("Registering the return sets Devuelto and the date; it cannot be before the loan, and it cannot be repeated", () -> {
                Loan loan = Loan.lend(sample, ana, DAY, DAY.plusDays(7), null);
                if (!throwsValidation(() -> loan.registerReturn(DAY.minusDays(1), null)) || loan.getStatus() != LoanStatus.ACTIVE) {
                    return false;
                }
                loan.registerReturn(DAY.plusDays(2), null);
                return loan.getStatus() == LoanStatus.RETURNED && loan.getReturnDate().equals(DAY.plusDays(2))
                        && throwsValidation(() -> loan.registerReturn(DAY.plusDays(3), null))
                        && loan.getReturnDate().equals(DAY.plusDays(2));
            });
            check("The note of the return is added to the observations of the loan", () -> {
                Loan with = Loan.lend(sample, ana, DAY, DAY.plusDays(7), "Con cargador");
                with.registerReturn(DAY.plusDays(1), "Pantalla rayada");
                Loan without = Loan.lend(sample, ana, DAY, DAY.plusDays(7), null);
                without.registerReturn(DAY.plusDays(1), "Sin novedad");
                Loan blank = Loan.lend(sample, ana, DAY, DAY.plusDays(7), "Original");
                blank.registerReturn(DAY.plusDays(1), "  ");
                return with.getObservations().equals("Con cargador | Pantalla rayada")
                        && without.getObservations().equals("Sin novedad") && blank.getObservations().equals("Original");
            });
            check("Days late: counted from the scheduled date to today (open) or to the real return date (returned); never negative", () -> {
                Loan open = Loan.lend(sample, ana, DAY, DAY.plusDays(7), null);
                Loan returned = Loan.lend(sample, ana, DAY, DAY.plusDays(7), null);
                returned.registerReturn(DAY.plusDays(10), null);
                return open.daysLate(DAY) == 0 && open.daysLate(DAY.plusDays(7)) == 0 && open.daysLate(DAY.plusDays(8)) == 1
                        && open.daysLate(DAY.plusDays(37)) == 30
                        && returned.daysLate(DAY) == 3 && returned.daysLate(DAY.plusDays(100)) == 3;
            });
            check("A loan is identified by its borrower type AND its number (the two tables count separately)", () -> {
                Loan a = Loan.restore(1, sample, ana, DAY, DAY, null, LoanStatus.ACTIVE, null);
                Loan b = Loan.restore(1, sample, teacher1, DAY, DAY, null, LoanStatus.ACTIVE, null);
                Loan c = Loan.restore(1, sample, ana, DAY, DAY, null, LoanStatus.ACTIVE, null);
                return !a.equals(b) && a.equals(c) && a.hashCode() == c.hashCode();
            });

            // ---- the pure calculations (streams) ----
            check("The totals by enum keep every constant, with 0 for the ones that have none, in declaration order", () -> {
                Map<LoanStatus, Long> counts = EnumCounts.countBy(LoanStatus.class,
                        List.of(Loan.restore(1, sample, ana, DAY, DAY, null, LoanStatus.ACTIVE, null)), Loan::getStatus);
                return counts.size() == 3 && counts.get(LoanStatus.ACTIVE) == 1L && counts.get(LoanStatus.RETURNED) == 0L
                        && counts.get(LoanStatus.OVERDUE) == 0L && counts.keySet().stream().toList().equals(List.of(LoanStatus.values()));
            });
            check("The overdue report puts the most delayed first (ties: the earlier loan), and counts by borrower type and category", () -> {
                Resource book = category(sample, "ZZP-C1", ResourceCategory.BOOK);
                Resource tablet = category(sample, "ZZP-C2", ResourceCategory.TECH_DEVICE);
                Loan small = Loan.restore(1, book, ana, DAY.minusDays(20), DAY.minusDays(3), null, LoanStatus.OVERDUE, null);
                Loan big = Loan.restore(2, tablet, teacher1, DAY.minusDays(60), DAY.minusDays(30), null, LoanStatus.OVERDUE, null);
                Loan tieOld = Loan.restore(3, book, ana, DAY.minusDays(40), DAY.minusDays(10), null, LoanStatus.OVERDUE, null);
                Loan tieNew = Loan.restore(4, book, ana, DAY.minusDays(30), DAY.minusDays(10), null, LoanStatus.OVERDUE, null);
                OverdueReport report = LoanAnalyzer.overdueReport(List.of(small, tieNew, big, tieOld), DAY);
                return report.total() == 4 && report.asOf().equals(DAY) && report.maxDaysLate() == 30
                        && report.loans().equals(List.of(big, tieOld, tieNew, small))
                        && report.byBorrowerType().get(BorrowerType.STUDENT) == 3L && report.byBorrowerType().get(BorrowerType.TEACHER) == 1L
                        && report.byCategory().get(ResourceCategory.BOOK) == 3L && report.byCategory().get(ResourceCategory.TECH_DEVICE) == 1L
                        && report.byCategory().get(ResourceCategory.LAB_EQUIPMENT) == 0L && report.byCategory().size() == 5;
            });
            check("An empty overdue report has 0 loans and 0 days; the listing counts every state", () -> {
                OverdueReport none = LoanAnalyzer.overdueReport(List.of(), DAY);
                LoanListing listing = LoanAnalyzer.listing(List.of(
                        Loan.restore(1, sample, ana, DAY, DAY, null, LoanStatus.ACTIVE, null),
                        Loan.restore(2, sample, ana, DAY, DAY, DAY, LoanStatus.RETURNED, null)));
                return none.total() == 0 && none.maxDaysLate() == 0 && none.byBorrowerType().get(BorrowerType.STUDENT) == 0L
                        && listing.total() == 2 && listing.byStatus().get(LoanStatus.ACTIVE) == 1L
                        && listing.byStatus().get(LoanStatus.RETURNED) == 1L && listing.byStatus().get(LoanStatus.OVERDUE) == 0L;
            });
            check("Result.map converts the data of a success and keeps a failure as it was", () -> {
                Result<Integer> ok = Result.ok(21, "listo");
                Result<Integer> bad = Result.fail("mal");
                Result<String> mapped = ok.map(n -> "n=" + n * 2);
                Result<String> failed = bad.map(n -> "no se llama");
                return mapped.isSuccess() && mapped.getData().equals("n=42") && mapped.getMessage().equals("listo")
                        && !failed.isSuccess() && failed.getMessage().equals("mal") && failed.getData() == null;
            });

            // ---- registering a loan (database) ----
            check("The clock of the controller gives today and the proposed return date (7 days)", () ->
                    loans.today().equals(DAY) && loans.defaultDueDate().equals(DAY.plusDays(7)));
            Resource book1 = savedResource(resources, "ZZP-L01");
            Loan[] lent = new Loan[1];
            check("Lend to a student: the loan is saved in prestamos_estudiante as Activo and the resource becomes Prestado", () -> {
                Result<Loan> r = loans.lend(book1, ana, DAY.plusDays(14), "Para la tarea");
                if (!r.isSuccess()) {
                    return false;
                }
                lent[0] = r.getData();
                return lent[0].getId() != null && statusInDb("ZZP-L01").equals("Prestado")
                        && scalar("SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-L01' AND pe.id_estudiante = 1 AND pe.fecha_prestamo = '2026-11-16' "
                        + "AND pe.fecha_devolucion_esperada = '2026-11-30' AND pe.fecha_devolucion_real IS NULL "
                        + "AND pe.estado_prestamo = 'Activo' AND pe.observaciones = 'Para la tarea'") == 1
                        && scalar("SELECT COUNT(*) FROM prestamos_profesor pp JOIN recursos r ON r.id_recurso = pp.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-L01'") == 0
                        && r.getMessage().contains("ZZP-L01") && r.getMessage().contains("2026-11-30");
            });
            Resource lab1 = savedResource(resources, "ZZP-L02");
            check("Lend to a teacher: the loan goes to prestamos_profesor, not to the student table", () ->
                    loans.lend(lab1, teacher1, DAY.plusDays(3), null).isSuccess() && statusInDb("ZZP-L02").equals("Prestado")
                            && scalar("SELECT COUNT(*) FROM prestamos_profesor pp JOIN recursos r ON r.id_recurso = pp.id_recurso "
                            + "WHERE r.codigo_recurso = 'ZZP-L02' AND pp.id_profesor = 1 AND pp.estado_prestamo = 'Activo' "
                            + "AND pp.observaciones IS NULL") == 1
                            && scalar("SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso "
                            + "WHERE r.codigo_recurso = 'ZZP-L02'") == 0);
            check("A resource that is already lent cannot be lent again: refused with the reason, and no second loan is saved", () -> {
                Result<Loan> again = loans.lend(withStatus(book1, ResourceStatus.AVAILABLE), teacher1, DAY.plusDays(3), null);
                return !again.isSuccess() && again.getMessage().contains("ya está prestado")
                        && openLoansOf("ZZP-L01") == 1 && statusInDb("ZZP-L01").equals("Prestado");
            });
            check("A resource in maintenance or written off cannot be lent", () -> {
                Resource inMaintenance = savedResource(resources, "ZZP-L03");
                Resource off = savedResource(resources, "ZZP-L04");
                execute("UPDATE recursos SET estado_recurso = 'En Mantenimiento' WHERE codigo_recurso = 'ZZP-L03'");
                execute("UPDATE recursos SET estado_recurso = 'Dado de Baja' WHERE codigo_recurso = 'ZZP-L04'");
                Result<Loan> m = loans.lend(inMaintenance, ana, DAY.plusDays(3), null);
                Result<Loan> o = loans.lend(off, ana, DAY.plusDays(3), null);
                return !m.isSuccess() && m.getMessage().contains("mantenimiento") && !o.isSuccess() && o.getMessage().contains("dado de baja")
                        && allLoansOf("ZZP-L03") + allLoansOf("ZZP-L04") == 0
                        && statusInDb("ZZP-L03").equals("En Mantenimiento") && statusInDb("ZZP-L04").equals("Dado de Baja");
            });
            check("A person who is not active cannot borrow (student and teacher): nothing is saved and the resource stays Disponible", () -> {
                Resource free = savedResource(resources, "ZZP-L05");
                Result<Loan> student = loans.lend(free, graduated, DAY.plusDays(3), null);
                Result<Loan> teacher = loans.lend(free, onLeave, DAY.plusDays(3), null);
                return !student.isSuccess() && student.getMessage().contains("Graduado")
                        && !teacher.isSuccess() && teacher.getMessage().contains("En licencia")
                        && allLoansOf("ZZP-L05") == 0 && statusInDb("ZZP-L05").equals("Disponible");
            });
            check("A wrong date refuses the loan and leaves the resource Disponible", () -> {
                Resource free = savedResource(resources, "ZZP-L06");
                Result<Loan> before = loans.lend(free, ana, DAY.minusDays(1), null);
                Result<Loan> tooLong = loans.lend(free, ana, DAY.plusDays(91), null);
                return !before.isSuccess() && !tooLong.isSuccess() && tooLong.getMessage().contains("90 días")
                        && allLoansOf("ZZP-L06") == 0 && statusInDb("ZZP-L06").equals("Disponible");
            });
            check("If saving the loan fails, the change of the resource is undone too (one transaction)", () -> {
                Resource free = savedResource(resources, "ZZP-L07");
                Borrower ghost = Borrower.student(999999, "Estudiante Inexistente", "000", null, null, StudentStatus.ACTIVE);
                Result<Loan> r = loans.lend(free, ghost, DAY.plusDays(3), null);
                return !r.isSuccess() && statusInDb("ZZP-L07").equals("Disponible") && allLoansOf("ZZP-L07") == 0;
            });
            check("If the state of the resource changed after it was read, the database decides: the stale copy cannot be lent", () -> {
                Resource stale = savedResource(resources, "ZZP-L08");
                execute("UPDATE recursos SET estado_recurso = 'Prestado' WHERE codigo_recurso = 'ZZP-L08'");
                Result<Loan> r = loans.lend(stale, ana, DAY.plusDays(3), null);
                return !r.isSuccess() && r.getMessage().contains("ya está prestado") && allLoansOf("ZZP-L08") == 0;
            });
            check("Searching lendable resources shows only the Disponible ones and explains when none is", () -> {
                Result<List<Resource>> lendable = loans.searchLendable("ZZP-L0");
                List<String> codes = lendable.getData().stream().map(Resource::getCode).toList();
                Result<List<Resource>> none = loans.searchLendable("ZZP-L01");
                return lendable.isSuccess() && codes.contains("ZZP-L00") && codes.contains("ZZP-L05") && codes.contains("ZZP-L06")
                        && !codes.contains("ZZP-L01") && !codes.contains("ZZP-L02") && !codes.contains("ZZP-L03") && !codes.contains("ZZP-L04")
                        && !none.isSuccess() && none.getMessage().contains("ninguno está disponible") && none.getMessage().contains("Prestado 1")
                        && loans.searchLendable("zzzz-no-existe").getData().isEmpty() && !loans.searchLendable("  ").isSuccess();
            });
            check("Searching borrowed resources shows only the Prestado ones and explains when none is", () -> {
                List<String> codes = loans.searchBorrowed("ZZP-L0").getData().stream().map(Resource::getCode).toList();
                Result<List<Resource>> none = loans.searchBorrowed("ZZP-L05");
                return codes.contains("ZZP-L01") && codes.contains("ZZP-L02") && !codes.contains("ZZP-L05") && !codes.contains("ZZP-L00")
                        && !none.isSuccess() && none.getMessage().contains("ninguno está prestado")
                        && loans.searchBorrowed("zzzz-no-existe").getData().isEmpty();
            });

            // ---- returning ----
            check("The open loan of a resource is found; a resource that is not out has none", () -> {
                Result<Loan> open = loans.openLoanOf(book1);
                Result<Loan> none = loans.openLoanOf(savedResource(resources, "ZZP-L09"));
                return open.isSuccess() && open.getData().getBorrower().id() == 1 && open.getData().getStatus() == LoanStatus.ACTIVE
                        && !none.isSuccess() && none.getMessage().contains("no tiene un préstamo pendiente");
            });
            check("Return on time: the loan is Devuelto with today's date, the resource is Disponible, and the note is kept", () -> {
                Loan open = loans.openLoanOf(book1).getData();
                Result<Loan> r = loans.giveBack(open, "Sin novedad");
                return r.isSuccess() && r.getMessage().contains("Disponible") && !r.getMessage().contains("retraso")
                        && statusInDb("ZZP-L01").equals("Disponible")
                        && scalar("SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-L01' AND pe.estado_prestamo = 'Devuelto' AND pe.fecha_devolucion_real = '2026-11-16' "
                        + "AND pe.observaciones = 'Para la tarea | Sin novedad'") == 1
                        && openLoansOf("ZZP-L01") == 0 && loans.openLoanOf(book1).getMessage().contains("no tiene");
            });
            check("After the return the resource can be lent again (a new loan, the old one stays in the history)", () ->
                    loans.lend(withStatus(book1, ResourceStatus.AVAILABLE), teacher1, DAY.plusDays(5), null).isSuccess()
                            && allLoansOf("ZZP-L01") == 2 && openLoansOf("ZZP-L01") == 1 && statusInDb("ZZP-L01").equals("Prestado"));
            check("Return of a teacher's loan goes to prestamos_profesor and says how many days late it was", () -> {
                execute("UPDATE prestamos_profesor SET fecha_prestamo = '2026-11-01', fecha_devolucion_esperada = '2026-11-08', estado_prestamo = 'Vencido' "
                        + "WHERE id_recurso = (SELECT id_recurso FROM recursos WHERE codigo_recurso = 'ZZP-L02')");
                Loan open = loans.openLoanOf(lab1).getData();
                Result<Loan> r = loans.giveBack(open, null);
                return r.isSuccess() && r.getMessage().contains("8 día(s) de retraso") && statusInDb("ZZP-L02").equals("Disponible")
                        && scalar("SELECT COUNT(*) FROM prestamos_profesor pp JOIN recursos r ON r.id_recurso = pp.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-L02' AND pp.estado_prestamo = 'Devuelto' AND pp.fecha_devolucion_real = '2026-11-16'") == 1
                        && r.getData().daysLate(DAY) == 8;
            });
            check("A loan cannot be returned twice, not even with a copy that was read before the first return", () -> {
                Resource twice = savedResource(resources, "ZZP-L10");
                loans.lend(twice, ana, DAY.plusDays(5), "Original");
                Loan first = loans.openLoanOf(twice).getData();
                Loan stale = loans.openLoanOf(twice).getData();
                Result<Loan> ok = loans.giveBack(first, "Primera");
                Result<Loan> again = loans.giveBack(first, "Otra vez");
                Result<Loan> old = loans.giveBack(stale, "Copia vieja");
                return ok.isSuccess() && !again.isSuccess() && again.getMessage().contains("ya fue devuelto")
                        && !old.isSuccess() && old.getMessage().contains("ya había sido devuelto")
                        && scalar("SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-L10' AND pe.observaciones = 'Original | Primera'") == 1
                        && statusInDb("ZZP-L10").equals("Disponible");
            });
            check("A return does not bring back a resource that was set aside meanwhile (the loan closes, the state stays)", () -> {
                Resource set = savedResource(resources, "ZZP-L11");
                loans.lend(set, ana, DAY.plusDays(5), null);
                Loan open = loans.openLoanOf(set).getData();
                execute("UPDATE recursos SET estado_recurso = 'Dado de Baja' WHERE codigo_recurso = 'ZZP-L11'");
                Result<Loan> r = loans.giveBack(open, null);
                return r.isSuccess() && statusInDb("ZZP-L11").equals("Dado de Baja") && openLoansOf("ZZP-L11") == 0;
            });
            check("The return date cannot be before the loan date; the loan stays open", () -> {
                Resource early = savedResource(resources, "ZZP-L12");
                loans.lend(early, ana, DAY.plusDays(5), null);
                Loan open = loans.openLoanOf(early).getData();
                clock.set(DAY.minusDays(1));
                Result<Loan> r = loans.giveBack(open, null);
                clock.set(DAY);
                return !r.isSuccess() && r.getMessage().contains("anterior") && openLoansOf("ZZP-L12") == 1 && statusInDb("ZZP-L12").equals("Prestado");
            });

            // ---- overdue loans ----
            Resource a = savedResource(resources, "ZZP-L20");
            Resource b = savedResource(resources, "ZZP-L21");
            Resource c = savedResource(resources, "ZZP-L22");
            Resource d = savedResource(resources, "ZZP-L23");
            insertLoan("ZZP-L20", "estudiante", 2, "2026-10-01", "2026-11-10", null, "Activo");     // 6 days late, still marked Activo
            insertLoan("ZZP-L21", "estudiante", 3, "2026-11-09", "2026-11-16", null, "Activo");     // due TODAY: not late yet
            insertLoan("ZZP-L22", "profesor", 2, "2026-11-10", "2026-11-17", null, "Activo");       // due tomorrow
            insertLoan("ZZP-L23", "profesor", 3, "2026-10-01", "2026-10-08", "2026-10-12", "Devuelto"); // was late but is back
            check("A loan whose scheduled date passed becomes Vencido in the database when the overdue report is asked", () -> {
                if (!stateOfLoan("estudiante", "ZZP-L20").equals("Activo")) {
                    return false;
                }
                Result<OverdueReport> r = loans.overdueReport();
                return r.isSuccess() && stateOfLoan("estudiante", "ZZP-L20").equals("Vencido")
                        && codesOf(r.getData()).contains("ZZP-L20");
            });
            check("The day it is due is not late yet; a returned loan is never in the report", () -> {
                List<String> codes = codesOf(loans.overdueReport().getData());
                return !codes.contains("ZZP-L21") && !codes.contains("ZZP-L22") && !codes.contains("ZZP-L23")
                        && stateOfLoan("estudiante", "ZZP-L21").equals("Activo") && stateOfLoan("profesor", "ZZP-L23").equals("Devuelto");
            });
            check("One day later the loan due today is Vencido (the report depends on the date, not on a stored flag)", () -> {
                clock.set(DAY.plusDays(1));
                Result<OverdueReport> r = loans.overdueReport();
                clock.set(DAY);
                boolean passed = r.isSuccess() && codesOf(r.getData()).contains("ZZP-L21") && !codesOf(r.getData()).contains("ZZP-L22")
                        && stateOfLoan("estudiante", "ZZP-L21").equals("Vencido") && r.getData().asOf().equals(DAY.plusDays(1));
                // time only goes forward in real life: put the loan back as it was so the next checks see today's world
                execute("UPDATE prestamos_estudiante SET estado_prestamo = 'Activo' WHERE id_recurso = "
                        + "(SELECT id_recurso FROM recursos WHERE codigo_recurso = 'ZZP-L21')");
                return passed && !codesOf(loans.overdueReport().getData()).contains("ZZP-L21");
            });
            check("markOverdue changes only Activo loans past their date, and a second call changes nothing", () -> {
                execute("UPDATE prestamos_estudiante SET estado_prestamo = 'Activo' WHERE id_recurso = (SELECT id_recurso FROM recursos WHERE codigo_recurso = 'ZZP-L20')");
                int first = daos.loanDao().markOverdue(DAY);
                int second = daos.loanDao().markOverdue(DAY);
                return first >= 1 && second == 0 && stateOfLoan("estudiante", "ZZP-L20").equals("Vencido")
                        && stateOfLoan("profesor", "ZZP-L22").equals("Activo") && stateOfLoan("profesor", "ZZP-L23").equals("Devuelto");
            });
            check("The report is sorted by days late (most delayed first), and its totals add up", () -> {
                OverdueReport report = loans.overdueReport().getData();
                List<Long> days = report.loans().stream().map(l -> l.daysLate(DAY)).toList();
                return days.equals(days.stream().sorted(java.util.Comparator.reverseOrder()).toList())
                        && report.byBorrowerType().values().stream().mapToLong(Long::longValue).sum() == report.total()
                        && report.byCategory().values().stream().mapToLong(Long::longValue).sum() == report.total()
                        && report.maxDaysLate() == days.get(0) && report.loans().stream().allMatch(l -> l.getStatus() == LoanStatus.OVERDUE);
            });
            check("When nothing is overdue the report is empty and says so (a day before every loan was due)", () -> {
                clock.set(LocalDate.of(2026, 1, 1));
                Result<OverdueReport> r = loans.overdueReport();
                clock.set(DAY);
                return r.isSuccess() && r.getData().total() == 0 && r.getData().maxDaysLate() == 0
                        && r.getMessage().equals("No hay préstamos vencidos al 2026-01-01.");
            });
            check("Returning an overdue loan removes it from the report and the message tells the delay", () -> {
                Loan open = loans.openLoanOf(a).getData();
                Result<Loan> r = loans.giveBack(open, null);
                return r.isSuccess() && r.getMessage().contains("6 día(s) de retraso") && !codesOf(loans.overdueReport().getData()).contains("ZZP-L20")
                        && statusInDb("ZZP-L20").equals("Disponible");
            });

            // ---- pending loans and the histories ----
            check("Pending loans are the open ones (Activo and Vencido), the earliest due date first, with totals per state", () -> {
                LoanListing pending = loans.pending().getData();
                List<LocalDate> dates = pending.loans().stream().map(Loan::getDueDate).toList();
                return pending.loans().stream().allMatch(l -> l.getStatus().isOpen())
                        && dates.equals(dates.stream().sorted().toList())
                        && pending.byStatus().get(LoanStatus.RETURNED) == 0L
                        && pending.byStatus().get(LoanStatus.ACTIVE) + pending.byStatus().get(LoanStatus.OVERDUE) == pending.total()
                        && pending.total() == scalar("SELECT (SELECT COUNT(*) FROM prestamos_estudiante WHERE estado_prestamo <> 'Devuelto') "
                        + "+ (SELECT COUNT(*) FROM prestamos_profesor WHERE estado_prestamo <> 'Devuelto')");
            });
            check("The history of a resource has every loan, the most recent first, and says so when there is none", () -> {
                execute("UPDATE prestamos_estudiante SET fecha_prestamo = '2026-11-10' WHERE id_recurso = "
                        + "(SELECT id_recurso FROM recursos WHERE codigo_recurso = 'ZZP-L01')");
                LoanListing history = loans.historyOfResource(book1).getData();
                Result<LoanListing> never = loans.historyOfResource(savedResource(resources, "ZZP-L30"));
                return history.total() == 2 && history.loans().get(0).getBorrowerType() == BorrowerType.TEACHER
                        && history.loans().get(1).getBorrowerType() == BorrowerType.STUDENT
                        && history.byStatus().get(LoanStatus.RETURNED) == 1L && history.byStatus().get(LoanStatus.ACTIVE) == 1L
                        && never.isSuccess() && never.getData().total() == 0 && never.getMessage().contains("nunca se ha prestado");
            });
            check("The history of a person counts only his or her kind of loans: student 1 and teacher 1 are different people", () -> {
                LoanListing student = loans.historyOfBorrower(ana).getData();
                LoanListing teacher = loans.historyOfBorrower(teacher1).getData();
                return student.loans().stream().allMatch(l -> l.getBorrowerType() == BorrowerType.STUDENT && l.getBorrower().id() == 1)
                        && teacher.loans().stream().allMatch(l -> l.getBorrowerType() == BorrowerType.TEACHER && l.getBorrower().id() == 1)
                        && student.loans().stream().map(l -> l.getResource().getCode()).toList().contains("ZZP-L01")
                        && !student.loans().stream().map(l -> l.getResource().getCode()).toList().contains("ZZP-L02")
                        && teacher.loans().stream().map(l -> l.getResource().getCode()).toList().contains("ZZP-L02");
            });
            check("A person with no loans gets a clear message", () -> {
                Result<LoanListing> r = loans.historyOfBorrower(Borrower.student(40, "Antonia Duarte", "x", null, null, StudentStatus.ACTIVE));
                return r.isSuccess() && r.getData().total() == 0 && r.getMessage().contains("no tiene préstamos");
            });

            // ---- the sample data of docs/database.sql ----
            check("Sample: at least 30 loans, in both tables, and every loan passes the rules of the entity when it is read", () ->
                    scalar("SELECT (SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso WHERE r.codigo_recurso NOT LIKE 'ZZP-%') "
                            + "+ (SELECT COUNT(*) FROM prestamos_profesor pp JOIN recursos r ON r.id_recurso = pp.id_recurso WHERE r.codigo_recurso NOT LIKE 'ZZP-%')") >= 30
                            && scalar("SELECT COUNT(*) FROM prestamos_estudiante") > 0 && scalar("SELECT COUNT(*) FROM prestamos_profesor") > 0
                            && loans.pending().isSuccess() && loans.historyOfResource(sampleResource(daos, "LIB-001")).isSuccess());
            check("Sample: every Prestado resource has exactly ONE open loan, and no other resource has an open loan", () ->
                    scalar("SELECT COUNT(*) FROM recursos r WHERE r.codigo_recurso NOT LIKE 'ZZP-%' AND r.estado_recurso = 'Prestado' AND "
                            + "(SELECT COUNT(*) FROM prestamos_estudiante p WHERE p.id_recurso = r.id_recurso AND p.estado_prestamo <> 'Devuelto') "
                            + "+ (SELECT COUNT(*) FROM prestamos_profesor p WHERE p.id_recurso = r.id_recurso AND p.estado_prestamo <> 'Devuelto') <> 1") == 0
                            && scalar("SELECT COUNT(*) FROM recursos r WHERE r.codigo_recurso NOT LIKE 'ZZP-%' AND r.estado_recurso <> 'Prestado' AND "
                            + "(SELECT COUNT(*) FROM prestamos_estudiante p WHERE p.id_recurso = r.id_recurso AND p.estado_prestamo <> 'Devuelto') "
                            + "+ (SELECT COUNT(*) FROM prestamos_profesor p WHERE p.id_recurso = r.id_recurso AND p.estado_prestamo <> 'Devuelto') > 0") == 0);
            check("Sample: the ten pending loans are there (as many as Prestado resources) and the five known overdue ones are reported, most delayed first", () -> {
                List<String> sampleOverdue = loans.overdueReport().getData().loans().stream()
                        .map(l -> l.getResource().getCode()).filter(code -> !code.startsWith("ZZP-")).toList();
                long borrowedSample = scalar("SELECT COUNT(*) FROM recursos WHERE estado_recurso = 'Prestado' AND codigo_recurso NOT LIKE 'ZZP-%'");
                long pendingSample = loans.pending().getData().loans().stream().filter(l -> !l.getResource().getCode().startsWith("ZZP-")).count();
                return borrowedSample == 10 && pendingSample == 10 && sampleOverdue.size() >= 5
                        && sampleOverdue.subList(0, 5).equals(List.of("LIB-001", "DEP-006", "TEC-006", "TEC-001", "LAB-002"));
            });
            check("Sample: the history of a graduated student and of a teacher on leave can still be read", () ->
                    loans.historyOfBorrower(graduated).getData().total() >= 1 && !graduated.active()
                            && loans.historyOfBorrower(onLeave).getData().total() >= 1 && !onLeave.active());
            check("Sample: the loans that came back were returned on or after the loan date, some of them late", () ->
                    scalar("SELECT COUNT(*) FROM (SELECT fecha_prestamo, fecha_devolucion_real FROM prestamos_estudiante WHERE estado_prestamo = 'Devuelto' "
                            + "UNION ALL SELECT fecha_prestamo, fecha_devolucion_real FROM prestamos_profesor WHERE estado_prestamo = 'Devuelto') x "
                            + "WHERE fecha_devolucion_real < fecha_prestamo") == 0
                            && scalar("SELECT COUNT(*) FROM (SELECT fecha_devolucion_esperada, fecha_devolucion_real FROM prestamos_estudiante WHERE estado_prestamo = 'Devuelto' "
                            + "UNION ALL SELECT fecha_devolucion_esperada, fecha_devolucion_real FROM prestamos_profesor WHERE estado_prestamo = 'Devuelto') x "
                            + "WHERE fecha_devolucion_real > fecha_devolucion_esperada") >= 5);
            check("The database is the last defence: a returned loan without a return date, or an open one with it, is refused", () ->
                    translatedFailure("INSERT INTO prestamos_estudiante (id_recurso, id_estudiante, fecha_prestamo, fecha_devolucion_esperada, estado_prestamo) "
                            + "SELECT id_recurso, 1, '2026-01-05', '2026-01-10', 'Devuelto' FROM recursos WHERE codigo_recurso = 'ZZP-L00'")
                            && translatedFailure("INSERT INTO prestamos_profesor (id_recurso, id_profesor, fecha_prestamo, fecha_devolucion_esperada, fecha_devolucion_real, estado_prestamo) "
                            + "SELECT id_recurso, 1, '2026-01-05', '2026-01-10', '2026-01-08', 'Activo' FROM recursos WHERE codigo_recurso = 'ZZP-L00'")
                            && translatedFailure("INSERT INTO prestamos_profesor (id_recurso, id_profesor, fecha_prestamo, fecha_devolucion_esperada, fecha_devolucion_real, estado_prestamo) "
                            + "SELECT id_recurso, 1, '2026-01-05', '2026-01-10', '2026-01-04', 'Devuelto' FROM recursos WHERE codigo_recurso = 'ZZP-L00'")
                            && translatedFailure("INSERT INTO prestamos_profesor (id_recurso, id_profesor, fecha_prestamo, fecha_devolucion_esperada, estado_prestamo) "
                            + "SELECT id_recurso, 1, '2026-01-10', '2026-01-05', 'Activo' FROM recursos WHERE codigo_recurso = 'ZZP-L00'"));
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Teacher findTeacher(DaoFactory daos, int id) throws DaoException {
        return daos.teacherDao().findAll().stream().filter(t -> t.getId() == id).findFirst().orElseThrow();
    }

    private static Resource savedResource(ResourceController resources, String code) {
        Resource resource = new Resource(code, "Recurso de prueba " + code, ResourceCategory.BOOK, null, null, "Biblioteca - Estante Z");
        Result<Resource> result = resources.create(resource);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Could not create " + code + ": " + result.getMessage());
        }
        return resource;
    }

    private static Resource sampleResource(DaoFactory daos, String code) throws DaoException {
        return daos.resourceDao().findAll().stream().filter(r -> r.getCode().equals(code)).findFirst().orElseThrow();
    }

    private static Resource category(Resource base, String code, ResourceCategory category) {
        Resource copy = new Resource(code, "Recurso " + code, category, null, null, "Biblioteca");
        copy.setId(base.getId());
        return copy;
    }

    private static Resource withStatus(Resource resource, ResourceStatus status) {
        resource.setStatus(status);
        return resource;
    }

    private static List<String> codesOf(OverdueReport report) {
        return report.loans().stream().map(l -> l.getResource().getCode()).toList();
    }

    private static void insertLoan(String code, String table, int borrowerId, String loanDate, String dueDate,
                                   String returnDate, String state) throws SQLException, DaoException {
        String column = table.equals("estudiante") ? "id_estudiante" : "id_profesor";
        execute("INSERT INTO prestamos_" + table + " (id_recurso, " + column + ", fecha_prestamo, fecha_devolucion_esperada, "
                + "fecha_devolucion_real, estado_prestamo) SELECT id_recurso, " + borrowerId + ", '" + loanDate + "', '" + dueDate + "', "
                + (returnDate == null ? "NULL" : "'" + returnDate + "'") + ", '" + state + "' FROM recursos WHERE codigo_recurso = '" + code + "'");
        if (!state.equals("Devuelto")) {
            execute("UPDATE recursos SET estado_recurso = 'Prestado' WHERE codigo_recurso = '" + code + "'");
        }
    }

    private static String stateOfLoan(String table, String code) throws SQLException, DaoException {
        return text("SELECT p.estado_prestamo FROM prestamos_" + table + " p JOIN recursos r ON r.id_recurso = p.id_recurso "
                + "WHERE r.codigo_recurso = '" + code + "' ORDER BY p.id_prestamo DESC LIMIT 1");
    }

    private static long openLoansOf(String code) throws SQLException, DaoException {
        return scalar("SELECT (SELECT COUNT(*) FROM prestamos_estudiante p JOIN recursos r ON r.id_recurso = p.id_recurso "
                + "WHERE r.codigo_recurso = '" + code + "' AND p.estado_prestamo <> 'Devuelto') "
                + "+ (SELECT COUNT(*) FROM prestamos_profesor p JOIN recursos r ON r.id_recurso = p.id_recurso "
                + "WHERE r.codigo_recurso = '" + code + "' AND p.estado_prestamo <> 'Devuelto')");
    }

    private static long allLoansOf(String code) throws SQLException, DaoException {
        return scalar("SELECT (SELECT COUNT(*) FROM prestamos_estudiante p JOIN recursos r ON r.id_recurso = p.id_recurso "
                + "WHERE r.codigo_recurso = '" + code + "') "
                + "+ (SELECT COUNT(*) FROM prestamos_profesor p JOIN recursos r ON r.id_recurso = p.id_recurso "
                + "WHERE r.codigo_recurso = '" + code + "')");
    }

    private static String statusInDb(String code) throws SQLException, DaoException {
        return text("SELECT estado_recurso FROM recursos WHERE codigo_recurso = '" + code + "'");
    }

    private static String text(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getString(1) : "(no existe)";
        }
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

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
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

    /** Deletes what this check created; the loans of those resources go with them (ON DELETE CASCADE). */
    private static void cleanUp() throws SQLException, DaoException {
        execute("DELETE FROM recursos WHERE codigo_recurso LIKE 'ZZP-%'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
