package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.InvoiceController;
import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.finance.BillingProfile;
import com.littlehorizons.erp.controller.finance.CufeGenerator;
import com.littlehorizons.erp.controller.finance.InvoiceCalculator;
import com.littlehorizons.erp.controller.finance.InvoiceDocument;
import com.littlehorizons.erp.controller.finance.InvoiceFileStore;
import com.littlehorizons.erp.controller.finance.InvoiceTextRenderer;
import com.littlehorizons.erp.controller.finance.SimulatedQr;
import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.InvoiceLine;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.model.enums.PaymentMethod;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.InvoiceSelection;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manual check of module 3.3, electronic invoices in TXT (plain Java). It creates its own students
 * (documents starting with 9980), guardians (9981) and concepts ZZFACT_..., issues invoices into a temporary
 * folder, and at the end deletes everything it created and gives back the receipt and invoice numbers it used.
 * Run main() from IntelliJ with the database loaded and config.txt in place.
 */
public final class InvoiceModuleCheck {

    private static int failures = 0;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 22, 10, 33);
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 10);

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        PaymentConceptController concepts = new PaymentConceptController(daos.paymentConceptDao(), logger);
        PaymentController payments = new PaymentController(daos.paymentDao(), daos.paymentConceptDao(),
                daos.studentConceptPriceDao(), daos.enrollmentDao(), daos.studentDao(), logger);
        BillingProfile profile = BillingProfile.from((key, defaultValue) -> defaultValue);
        Path folder = Files.createTempDirectory("invoices-check");
        AtomicReference<LocalDateTime> clock = new AtomicReference<>(NOW);
        InvoiceController invoices = new InvoiceController(daos.invoiceDao(), daos.paymentDao(), daos.studentDao(), () -> profile,
                () -> new InvoiceFileStore(folder), clock::get, logger);

        try {
            cleanUp();

            // ---- entities: a line and an invoice can never be inconsistent ----
            Guardian client = guardian("1020304050", "Marta", "Calle 10 # 20-30, Bogotá");
            Student pupil = student("99800009999", "Prueba");
            check("A line needs a description, a quantity of at least 1, a VAT between 0 and 100 and a positive unit value", () ->
                    throwsValidation(() -> new InvoiceLine(1, "REC-1", " ", 1, new BigDecimal("10"), 0, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "x".repeat(201), 1, new BigDecimal("10"), 0, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "Libro", 0, new BigDecimal("10"), 0, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "Libro", 1, new BigDecimal("10"), 101, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "Libro", 1, new BigDecimal("10"), -1, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "Libro", 1, BigDecimal.ZERO, 0, BigDecimal.ZERO))
                            && throwsValidation(() -> new InvoiceLine(1, "REC-1", "Libro", 1, new BigDecimal("10"), 19, new BigDecimal("-1"))));
            check("A line calculates its subtotal (quantity x unit value) and its total (subtotal + VAT)", () -> {
                InvoiceLine line = new InvoiceLine(1, "REC-1", " Cuaderno ", 3, new BigDecimal("1000"), 19, new BigDecimal("570"));
                return line.getSubtotal().compareTo(new BigDecimal("3000")) == 0 && line.getTotal().compareTo(new BigDecimal("3570")) == 0
                        && line.getDescription().equals("Cuaderno") && line.getSubtotal().scale() == 2 && line.getTotal().scale() == 2;
            });
            // 119000 with 19% VAT, 1000.01 with 19% VAT (cents!) and 30000 without VAT
            Payment pBooks = fakePayment(pupil, "Libros Zzfact", 19, "119000", null, 11);
            Payment pWorkshop = fakePayment(pupil, "Taller Zzfact", 19, "1000.01", null, 12);
            Payment pCert = fakePayment(pupil, "Certificado Zzfact", 0, "30000", null, 13);
            Payment pTuition = fakePayment(pupil, "Cuota Zzfact", 0, "600000", 2, 14);
            check("A payment is split into value without VAT and VAT, and both add up to what was paid, to the cent", () -> {
                InvoiceLine books = InvoiceCalculator.lineOf(pBooks);
                InvoiceLine workshop = InvoiceCalculator.lineOf(pWorkshop);
                InvoiceLine cert = InvoiceCalculator.lineOf(pCert);
                return books.getUnitValue().compareTo(new BigDecimal("100000.00")) == 0 && books.getVatAmount().compareTo(new BigDecimal("19000.00")) == 0
                        && workshop.getUnitValue().compareTo(new BigDecimal("840.34")) == 0 && workshop.getVatAmount().compareTo(new BigDecimal("159.67")) == 0
                        && workshop.getTotal().compareTo(new BigDecimal("1000.01")) == 0
                        && cert.getUnitValue().compareTo(new BigDecimal("30000.00")) == 0 && cert.getVatAmount().signum() == 0
                        && books.getPaymentId() == 11 && books.getReceiptNumber().equals("REC-000011") && books.getQuantity() == 1;
            });
            check("The description says the concept and year, and the month of a monthly installment", () ->
                    InvoiceCalculator.lineOf(pBooks).getDescription().equals("Libros Zzfact 2026")
                            && InvoiceCalculator.lineOf(pTuition).getDescription().equals("Cuota Zzfact - cuota de febrero 2026"));

            List<InvoiceLine> lines = Stream.of(pBooks, pWorkshop, pCert).map(InvoiceCalculator::lineOf).toList();
            check("An invoice needs 1 to 30 lines, no repeated payment, and its totals come from the lines", () -> {
                Invoice invoice = new Invoice(pupil, client, NOW, lines);
                List<InvoiceLine> thirtyOne = new ArrayList<>();
                for (int i = 1; i <= 31; i++) {
                    thirtyOne.add(new InvoiceLine(i, "REC-" + i, "Linea " + i, 1, BigDecimal.TEN, 0, BigDecimal.ZERO));
                }
                return throwsValidation(() -> new Invoice(pupil, client, NOW, List.of()))
                        && throwsValidation(() -> new Invoice(pupil, client, NOW, List.of(lines.get(0), lines.get(0))))
                        && throwsValidation(() -> new Invoice(pupil, client, NOW, thirtyOne))
                        && new Invoice(pupil, client, NOW, thirtyOne.subList(0, 30)).getLines().size() == 30
                        && invoice.getSubtotal().compareTo(new BigDecimal("130840.34")) == 0
                        && invoice.getVatTotal().compareTo(new BigDecimal("19159.67")) == 0
                        && invoice.getTotal().compareTo(new BigDecimal("150000.01")) == 0;
            });
            check("The invoice number is FE-000123 and the file factura_000123.txt; both are empty until it is saved", () -> {
                Invoice invoice = new Invoice(pupil, client, NOW, lines);
                boolean empty = invoice.getNumber() == null && invoice.getFileName() == null;
                invoice.setSequence(123);
                return empty && invoice.getNumber().equals("FE-000123") && invoice.getFileName().equals("factura_000123.txt")
                        && Invoice.numberOf(7).equals("FE-000007") && Invoice.sequenceOf("FE-000042") == 42;
            });
            check("A typed invoice number is accepted as 15, fe-000015 or FE-000015 and rejected otherwise", () ->
                    Invoice.normalizeNumber("15").equals("FE-000015") && Invoice.normalizeNumber(" fe-000015 ").equals("FE-000015")
                            && Invoice.normalizeNumber("FE-000015").equals("FE-000015")
                            && throwsValidation(() -> Invoice.normalizeNumber("0")) && throwsValidation(() -> Invoice.normalizeNumber("FE-000000"))
                            && throwsValidation(() -> Invoice.normalizeNumber("abc")) && throwsValidation(() -> Invoice.normalizeNumber(""))
                            && throwsValidation(() -> Invoice.normalizeNumber("1234567")) && throwsValidation(() -> Invoice.normalizeNumber(null))
                            && throwsValidation(() -> Invoice.normalizeNumber("FE-12")));

            // ---- billing profile (the school data and the simulated resolution) ----
            check("The school data has valid defaults: NIT written with dots, resolution valid for 24 months, range FE-000001 to FE-050000", () -> {
                return profile.nitFormatted().equals("901.234.567-8") && profile.nitDigits().equals("901234567")
                        && profile.resolutionValidUntil().equals(LocalDate.of(2027, 12, 15))
                        && profile.resolutionText().contains("18760000001") && profile.resolutionText().contains("2025-12-15")
                        && profile.resolutionText().contains("FE-000001 a FE-050000") && profile.resolutionText().contains("2027-12-15")
                        && profile.resolutionText().contains("SIMULADA") && BillingProfile.SCHOOL_NAME.equals("Colegio Internacional Little Horizons S.A.S.");
            });
            check("A wrong billing key in config.txt is refused with a message that says what to fix", () ->
                    invalidProfile("billing.nit", "123") && invalidProfile("billing.nit", "90123456-8") && invalidProfile("billing.resolution.number", "abc")
                            && invalidProfile("billing.resolution.from", "0") && invalidProfile("billing.resolution.from", "x")
                            && invalidProfile("billing.resolution.to", "1000000") && invalidProfile("billing.resolution.date", "15/12/2025")
                            && invalidProfile("billing.phone", "  ") && invalidProfile("billing.address", "")
                            && invalidRange("10", "5")
                            && BillingProfile.from((k, d) -> k.equals("billing.nit") ? "800123456-1" : d).nitFormatted().equals("800.123.456-1"));

            // ---- CUFE ----
            Invoice fixed = new Invoice(pupil, client, NOW, lines);
            fixed.setSequence(1);
            fixed.setCufe(CufeGenerator.generate(fixed, profile));
            fixed.setResolution(profile.resolutionText());
            check("The CUFE is the SHA-384 of the invoice data in a fixed order: 96 hexadecimal characters", () -> {
                String source = "FE-000001" + "2026-11-15" + "22:10:33-05:00" + "130840.34" + "01" + "19159.67" + "04" + "0.00" + "03" + "0.00"
                        + "150000.01" + "901234567" + "1020304050" + "fc8eab76-ce3a-4b5c-9d41-2e6a1d7f0b93" + "2";
                String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(source.getBytes(StandardCharsets.UTF_8)));
                return fixed.getCufe().equals(expected) && fixed.getCufe().matches("[0-9a-f]{96}");
            });
            check("The same data gives the same CUFE and changing ONE detail (a cent, the client, the second, the number) changes it", () -> {
                Invoice sameData = copy(pupil, client, NOW, lines, 1);
                Invoice otherCent = copy(pupil, client, NOW, List.of(lines.get(0), lines.get(1), new InvoiceLine(13, "REC-000013", "Certificado Zzfact 2026", 1,
                        new BigDecimal("30000.01"), 0, BigDecimal.ZERO)), 1);
                Invoice otherClient = copy(pupil, guardian("1020304051", "Marta", "Calle 10 # 20-30"), NOW, lines, 1);
                Invoice otherSecond = copy(pupil, client, NOW.plusSeconds(1), lines, 1);
                Invoice otherNumber = copy(pupil, client, NOW, lines, 2);
                Set<String> all = Set.of(fixed.getCufe(), CufeGenerator.generate(otherCent, profile), CufeGenerator.generate(otherClient, profile),
                        CufeGenerator.generate(otherSecond, profile), CufeGenerator.generate(otherNumber, profile));
                return CufeGenerator.generate(sameData, profile).equals(fixed.getCufe()) && all.size() == 5
                        && !CufeGenerator.generate(fixed, BillingProfile.from((k, d) -> k.equals("billing.technical.key") ? "otra-clave" : d)).equals(fixed.getCufe());
            });

            // ---- QR ----
            List<String> qrContent = SimulatedQr.content(fixed, profile);
            check("The QR content carries the number, date, NIT, client, values and the CUFE", () -> {
                String all = String.join("\n", qrContent);
                return all.contains("NumFac: FE-000001") && all.contains("FecFac: 2026-11-15") && all.contains("HorFac: 22:10:33-05:00")
                        && all.contains("NitFac: 901234567") && all.contains("DocAdq: 1020304050") && all.contains("ValFac: 130840.34")
                        && all.contains("ValIva: 19159.67") && all.contains("ValTolFac: 150000.01") && all.contains("CUFE: " + fixed.getCufe())
                        && all.contains("documentkey=" + fixed.getCufe());
            });
            check("The QR drawing is a 25x25 square of text blocks with the three corner squares, and it depends only on the content", () -> {
                List<String> drawing = SimulatedQr.draw(qrContent);
                Invoice changed = copy(pupil, client, NOW, lines, 2);
                changed.setCufe(CufeGenerator.generate(changed, profile));
                String dark = "██";
                String light = "  ";
                return drawing.size() == 25 && drawing.stream().allMatch(row -> row.length() == 50)
                        && drawing.get(0).startsWith(dark.repeat(7) + light) && drawing.get(6).startsWith(dark.repeat(7) + light)
                        && drawing.get(1).startsWith(dark + light.repeat(5) + dark) && drawing.get(2).startsWith(dark + light + dark.repeat(3) + light + dark)
                        && drawing.get(0).endsWith(light + dark.repeat(7)) && drawing.get(24).startsWith(dark.repeat(7) + light)
                        && drawing.get(18).startsWith(dark.repeat(7)) && drawing.get(17).startsWith(light.repeat(7)) // bottom-left square starts at row 18
                        && drawing.get(6).substring(14, 20).equals(light + dark + light)  // timing line
                        && drawing.equals(SimulatedQr.draw(qrContent))
                        && !drawing.equals(SimulatedQr.draw(SimulatedQr.content(changed, profile)));
            });

            // ---- the text of the invoice ----
            List<String> text = InvoiceTextRenderer.render(fixed, profile);
            String all = String.join("\n", text);
            check("The TXT has the school data: name, NIT, address, phone and email", () ->
                    all.contains("Colegio Internacional Little Horizons S.A.S.") && all.contains("NIT 901.234.567-8")
                            && all.contains("Carrera 15 # 85-30, Bogotá D.C.") && all.contains("(601) 745 2100") && all.contains("facturacion@littlehorizons.edu.co"));
            check("The TXT has the client (name, document, address), the invoice number and the issue date", () ->
                    all.contains("Marta Prueba") && all.contains("CC 1020304050") && all.contains("Calle 10 # 20-30, Bogotá")
                            && all.contains("FACTURA ELECTRÓNICA DE VENTA") && all.contains("No. FE-000001") && all.contains("2026-11-15 22:10:33")
                            && all.contains("Estudiante") && all.contains("Prueba"));
            check("The TXT lists each concept with description, quantity, unit value and subtotal", () ->
                    all.contains("Descripción") && all.contains("Cant.") && all.contains("Valor unitario") && all.contains("Subtotal")
                            && all.contains("Libros Zzfact 2026") && all.contains("$100.000") && all.contains("Taller Zzfact 2026") && all.contains("$840,34")
                            && all.contains("Certificado Zzfact 2026") && all.contains("$30.000") && all.contains("Recibo REC-000011"));
            check("The TXT shows subtotal, VAT by rate and total", () ->
                    all.contains("Subtotal (sin IVA):") && all.contains("$130.840,34") && all.contains("IVA 0%:") && all.contains("IVA 19%:")
                            && all.contains("$19.159,67") && all.contains("TOTAL A PAGAR (COP):") && all.contains("$150.000,01"));
            check("The TXT shows the CUFE, the simulated resolution and the QR (content and drawing)", () -> {
                int cufeLine = text.indexOf("CUFE (SHA-384):");
                return cufeLine > 0 && (text.get(cufeLine + 1).trim() + text.get(cufeLine + 2).trim()).equals(fixed.getCufe())
                        && all.contains("Resolución DIAN No. 18760000001") && all.contains("SIMULADA") && all.contains("CÓDIGO QR (SIMULADO)")
                        && all.contains("ValTolFac: 150000.01") && all.contains("█") && all.contains("no es una factura real");
            });
            check("No line of the TXT is wider than 78 characters, even with a very long description, and the text is always the same", () -> {
                String longName = "Servicio educativo complementario de refuerzo en matemáticas, lectura crítica y ciencias naturales para estudiantes de bachillerato";
                Invoice longOne = new Invoice(pupil, client, NOW, List.of(new InvoiceLine(5, "REC-000005", longName, 2, new BigDecimal("12345.67"), 19,
                        new BigDecimal("4691.35"))));
                longOne.setSequence(9);
                longOne.setCufe(CufeGenerator.generate(longOne, profile));
                longOne.setResolution(profile.resolutionText());
                List<String> longText = InvoiceTextRenderer.render(longOne, profile);
                return text.stream().allMatch(l -> l.length() <= 78) && longText.stream().allMatch(l -> l.length() <= 78)
                        && longText.stream().anyMatch(l -> l.contains("Servicio educativo")) && String.join(" ", longText).contains("naturales")
                        && InvoiceTextRenderer.render(fixed, profile).equals(text);
            });
            check("A client without a phone, email or address does not break the text", () -> {
                Guardian bare = new Guardian(DocumentType.CE, "77777", "Sin", "Datos", "Tutor legal", "3000000000");
                Invoice invoice = copy(pupil, bare, NOW, lines, 3);
                invoice.setCufe(CufeGenerator.generate(invoice, profile));
                invoice.setResolution(profile.resolutionText());
                String rendered = String.join("\n", InvoiceTextRenderer.render(invoice, profile));
                return rendered.contains("CE 77777") && rendered.contains("(no registrada)");
            });
            check("The user chooses payments with «1,3» or «todos»; wrong lists are refused", () ->
                    InvoiceSelection.parse("1,3", 4).equals(List.of(1, 3)) && InvoiceSelection.parse(" 2 , 1 ", 4).equals(List.of(2, 1))
                            && InvoiceSelection.parse("TODOS", 3).equals(List.of(1, 2, 3)) && InvoiceSelection.parse("4", 4).equals(List.of(4))
                            && throwsValidation(() -> InvoiceSelection.parse("0", 4)) && throwsValidation(() -> InvoiceSelection.parse("5", 4))
                            && throwsValidation(() -> InvoiceSelection.parse("1,1", 4)) && throwsValidation(() -> InvoiceSelection.parse("a", 4))
                            && throwsValidation(() -> InvoiceSelection.parse("", 4)) && throwsValidation(() -> InvoiceSelection.parse("1,", 4))
                            && throwsValidation(() -> InvoiceSelection.parse("-1", 4)) && throwsValidation(() -> InvoiceSelection.parse("todos", 31)));

            // ---- data for the database checks ----
            PaymentConcept books = concept("ZZFACT_LIBROS", "Libros Zzfact", 119000, 19, false);
            PaymentConcept workshop = concept("ZZFACT_TALLER", "Taller Zzfact", 250000, 19, false);
            PaymentConcept certificate = concept("ZZFACT_CERT", "Certificado Zzfact", 30000, 0, false);
            PaymentConcept tuition = concept("ZZFACT_CUOTA", "Cuota Zzfact", 600000, 0, true);
            for (PaymentConcept c : List.of(books, workshop, certificate, tuition)) {
                concepts.create(c);
            }
            Guardian mother = guardian("99810000000", "Madre", "Calle 1 # 2-3, Bogotá");
            mother.setFinanciallyResponsible(true);
            Guardian father = guardian("99810000001", "Padre", "Carrera 4 # 5-6, Bogotá");
            Guardian noAddress = guardian("99810000002", "Tio", null);
            Guardian beaMother = guardian("99810000003", "MadreBea", "Calle 7 # 8-9, Bogotá");
            Student ana = student("99800000000", "FactA");
            Student bea = student("99800000001", "FactB");
            Student nobody = student("99800000002", "FactC");
            students.register(ana, new ArrayList<>(List.of(new StudentGuardian(mother, true, true),
                    new StudentGuardian(father, false, false), new StudentGuardian(noAddress, false, false))));
            students.register(bea, new ArrayList<>(List.of(new StudentGuardian(beaMother, true, true))));
            students.register(nobody, new ArrayList<>(List.of(new StudentGuardian(guardian("99810000004", "MadreC", "Calle 9"), true, true))));
            mother = daos.studentDao().findGuardians(ana.getId()).stream().map(StudentGuardian::getGuardian)
                    .filter(g -> g.getDocumentNumber().equals("99810000000")).findFirst().orElseThrow();
            Guardian motherOfAna = mother;
            Guardian fatherOfAna = daos.studentDao().findGuardians(ana.getId()).stream().map(StudentGuardian::getGuardian)
                    .filter(g -> g.getDocumentNumber().equals("99810000001")).findFirst().orElseThrow();
            Guardian noAddressOfAna = daos.studentDao().findGuardians(ana.getId()).stream().map(StudentGuardian::getGuardian)
                    .filter(g -> g.getDocumentNumber().equals("99810000002")).findFirst().orElseThrow();
            Guardian motherOfBea = daos.studentDao().findGuardians(bea.getId()).get(0).getGuardian();
            execute("INSERT INTO matriculas (id_estudiante, id_grado, anio_academico, estado_matricula, fecha_matricula) "
                    + "SELECT id_estudiante, (SELECT MIN(id_grado) FROM grados), 2026, 'Activa', '2026-01-15' FROM estudiantes "
                    + "WHERE numero_documento = '99800000000'");
            Payment aBooks = pay(payments, ana, books, motherOfAna, PaymentMethod.CASH, null);
            Payment aWorkshop = payCents(payments, ana, workshop, motherOfAna, "1000.01");
            Payment aCert = pay(payments, ana, certificate, fatherOfAna, PaymentMethod.TRANSFER, null);
            Payment aFeb = pay(payments, ana, tuition, motherOfAna, PaymentMethod.CASH, 2);
            List<Payment> months = new ArrayList<>();
            for (int month = 3; month <= 8; month++) {
                months.add(pay(payments, ana, tuition, motherOfAna, PaymentMethod.CARD, month));
            }
            Payment bBooks = pay(payments, bea, books, motherOfBea, PaymentMethod.CASH, null);
            long counterStart = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'");

            // ---- the payments that can still be invoiced ----
            check("Only payments that are in no invoice are offered, oldest first, each student his own", () -> {
                List<Payment> pending = invoices.unbilledPayments(ana).getData();
                return pending.size() == 10 && pending.stream().allMatch(p -> p.getStudent().getId().equals(ana.getId()))
                        && pending.get(0).getId().equals(aBooks.getId())
                        && invoices.unbilledPayments(bea).getData().size() == 1 && invoices.unbilledPayments(nobody).getData().isEmpty();
            });
            check("Who to invoice: the guardian who paid, or the financially responsible guardian when there are several payers or none", () ->
                    invoices.suggestedClient(ana, List.of(aBooks, aWorkshop)).getData().getId().equals(motherOfAna.getId())
                            && invoices.suggestedClient(ana, List.of(aCert)).getData().getId().equals(fatherOfAna.getId())
                            && invoices.suggestedClient(ana, List.of(aBooks, aCert)).getData().getId().equals(motherOfAna.getId())
                            && invoices.suggestedClient(ana, List.of(fakePayment(ana, "Libros Zzfact", 19, "100", null, 99))).getData().getId().equals(motherOfAna.getId())
                            && invoices.suggestedClient(ana, List.of(aCert, fakePayment(ana, "Libros Zzfact", 19, "100", null, 98))).getData().getId().equals(motherOfAna.getId())
                            && invoices.suggestedClient(bea, List.of(bBooks)).getData().getId().equals(motherOfBea.getId()));

            // ---- issuing ----
            Result<InvoiceDocument> first = invoices.issue(ana, List.of(aBooks, aWorkshop, aCert, aFeb), motherOfAna);
            String firstNumber = Invoice.numberOf((int) counterStart + 1);
            check("Issue an invoice: it gets the next consecutive number, the totals are exact and the success message says it", () -> {
                if (!first.isSuccess()) {
                    System.out.println("      " + first.getMessage());
                    return false;
                }
                Invoice invoice = first.getData().invoice();
                return invoice.getNumber().equals(firstNumber) && first.getMessage().contains(firstNumber) && invoice.getId() != null
                        && invoice.getSubtotal().compareTo(new BigDecimal("730840.34")) == 0 && invoice.getVatTotal().compareTo(new BigDecimal("19159.67")) == 0
                        && invoice.getTotal().compareTo(new BigDecimal("750000.01")) == 0 && invoice.getLines().size() == 4
                        && invoice.getCufe().matches("[0-9a-f]{96}") && invoice.getResolution().equals(profile.resolutionText())
                        && invoice.getIssuedAt().equals(NOW);
            });
            check("The TXT file is written with the unique name factura_NNNNNN.txt and holds the same text that is shown", () -> {
                Path file = folder.resolve("factura_" + String.format("%06d", counterStart + 1) + ".txt");
                return first.getData().file().equals(file) && Files.isRegularFile(file) && Files.readAllLines(file, StandardCharsets.UTF_8).equals(first.getData().text())
                        && Files.readString(file).contains(first.getData().invoice().getCufe().substring(0, 48))
                        && Files.readString(file).contains(first.getData().invoice().getCufe().substring(48)) && Files.readString(file).contains("Cuota Zzfact - cuota de febrero 2026")
                        && !Files.readString(file).contains("\u0000");
            });
            check("The invoice is saved in the database with its lines, and the stored totals add up", () -> {
                String number = firstNumber;
                return scalar("SELECT COUNT(*) FROM facturas WHERE numero_factura = '" + number + "' AND subtotal = 730840.34 AND valor_iva = 19159.67 AND total = 750000.01"
                        + " AND CHAR_LENGTH(cufe) = 96 AND nombre_archivo = 'factura_" + number.substring(3) + ".txt' AND id_acudiente = " + motherOfAna.getId()
                        + " AND id_estudiante = " + ana.getId()) == 1
                        && scalar("SELECT COUNT(*) FROM detalle_factura d JOIN facturas f ON f.id_factura = d.id_factura WHERE f.numero_factura = '" + number + "'") == 4
                        && scalar("SELECT ROUND(SUM(d.total) * 100) FROM detalle_factura d JOIN facturas f ON f.id_factura = d.id_factura WHERE f.numero_factura = '" + number + "'") == 75000001
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == counterStart + 1;
            });
            check("Invoiced payments are no longer offered", () -> {
                List<Payment> pending = invoices.unbilledPayments(ana).getData();
                return pending.size() == 6 && pending.stream().noneMatch(p -> Set.of(aBooks.getId(), aWorkshop.getId(), aCert.getId(), aFeb.getId()).contains(p.getId()));
            });
            check("A payment already invoiced cannot be invoiced again, and no number is used", () -> {
                Result<InvoiceDocument> again = invoices.issue(ana, List.of(aBooks), motherOfAna);
                return !again.isSuccess() && again.getMessage().contains("ya fue facturado")
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == counterStart + 1
                        && scalar("SELECT COUNT(*) FROM detalle_factura WHERE id_pago = " + aBooks.getId()) == 1;
            });
            check("The DAO refuses by itself a payment that is already in an invoice (even without going through the controller), and the number is given back", () -> {
                Invoice sneaky = new Invoice(ana, motherOfAna, NOW, List.of(InvoiceCalculator.lineOf(aBooks)));
                try {
                    daos.invoiceDao().create(sneaky, i -> { });
                    return false;
                } catch (ValidationException e) {
                    return e.getMessage().contains("ya fue facturado en la factura " + firstNumber) && e.getMessage().contains(aBooks.getReceiptNumber())
                            && sneaky.getSequence() == 0 && sneaky.getNumber() == null
                            && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == counterStart + 1;
                }
            });
            Result<InvoiceDocument> second = invoices.issue(ana, List.of(months.get(0)), fatherOfAna);
            check("The next invoice gets the next number with no gap, even after failed attempts; it can be issued to another guardian of the student", () ->
                    second.isSuccess() && second.getData().invoice().getNumber().equals(Invoice.numberOf((int) counterStart + 2))
                            && second.getData().invoice().getClient().getId().equals(fatherOfAna.getId()) && second.getData().invoice().getVatTotal().signum() == 0
                            && second.getData().invoice().getTotal().compareTo(new BigDecimal("600000.00")) == 0);

            // ---- client and payment rules ----
            long before = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'");
            check("Invoice rules: a client is required, must be a guardian of the student and must have an address; payments must be his and not invoiced yet", () -> {
                Result<InvoiceDocument> noClient = invoices.issue(ana, List.of(months.get(1)), null);
                Result<InvoiceDocument> stranger = invoices.issue(ana, List.of(months.get(1)), motherOfBea);
                Result<InvoiceDocument> noAddr = invoices.issue(ana, List.of(months.get(1)), noAddressOfAna);
                Result<InvoiceDocument> otherStudent = invoices.issue(ana, List.of(bBooks), motherOfAna);
                Result<InvoiceDocument> nothing = invoices.issue(ana, List.of(), motherOfAna);
                Result<InvoiceDocument> fake = invoices.issue(ana, List.of(fakePayment(ana, "Libros Zzfact", 19, "100", null, 987654)), motherOfAna);
                return !noClient.isSuccess() && noClient.getMessage().contains("acudiente")
                        && !stranger.isSuccess() && stranger.getMessage().contains("no es acudiente")
                        && !noAddr.isSuccess() && noAddr.getMessage().contains("dirección")
                        && !otherStudent.isSuccess() && otherStudent.getMessage().contains("ya fue facturado")
                        && !nothing.isSuccess() && nothing.getMessage().contains("al menos un pago")
                        && !fake.isSuccess()
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == before
                        && invoices.unbilledPayments(bea).getData().size() == 1;
            });

            // ---- the resolution ----
            check("The resolution is checked: not before its date, not after 24 months, and the number must be inside its range; nothing is used", () -> {
                clock.set(LocalDateTime.of(2025, 12, 14, 10, 0));
                Result<InvoiceDocument> early = invoices.issue(ana, List.of(months.get(1)), motherOfAna);
                clock.set(LocalDateTime.of(2027, 12, 16, 10, 0));
                Result<InvoiceDocument> expired = invoices.issue(ana, List.of(months.get(1)), motherOfAna);
                clock.set(LocalDateTime.of(2027, 12, 15, 23, 59));
                Result<InvoiceDocument> lastDay = invoices.issue(ana, List.of(months.get(1)), motherOfAna);
                clock.set(NOW);
                BillingProfile tight = BillingProfile.from((k, d) -> k.equals("billing.resolution.to") ? String.valueOf(before) : d);
                InvoiceController narrow = new InvoiceController(daos.invoiceDao(), daos.paymentDao(), daos.studentDao(), () -> tight,
                        () -> new InvoiceFileStore(folder), clock::get, logger);
                Result<InvoiceDocument> outOfRange = narrow.issue(ana, List.of(months.get(2)), motherOfAna);
                boolean rejected = !early.isSuccess() && early.getMessage().contains("empieza el 2025-12-15")
                        && !expired.isSuccess() && expired.getMessage().contains("venció el 2027-12-15")
                        && !outOfRange.isSuccess() && outOfRange.getMessage().contains("rango") && outOfRange.getMessage().contains(Invoice.numberOf((int) before + 2));
                boolean lastDayWorked = lastDay.isSuccess();
                return rejected && lastDayWorked && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == before + 1
                        && scalar("SELECT COUNT(*) FROM facturas WHERE numero_factura = '" + Invoice.numberOf((int) before + 2) + "'") == 0;
            });

            // ---- two people at the same time ----
            long beforeRace = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'");
            check("Two people invoicing the SAME payment at the same time: exactly one invoice is created", () -> {
                Payment target = months.get(3);
                List<Result<InvoiceDocument>> results = runTogether(List.of(
                        () -> invoices.issue(ana, List.of(target), motherOfAna), () -> invoices.issue(ana, List.of(target), fatherOfAna)));
                long ok = results.stream().filter(Result::isSuccess).count();
                return ok == 1 && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == beforeRace + 1
                        && scalar("SELECT COUNT(*) FROM detalle_factura WHERE id_pago = " + target.getId()) == 1;
            });
            check("Several invoices at the same time get different consecutive numbers with no gaps", () -> {
                long start = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'");
                List<Callable<Result<InvoiceDocument>>> jobs = new ArrayList<>();
                jobs.add(() -> invoices.issue(ana, List.of(months.get(2)), motherOfAna));
                jobs.add(() -> invoices.issue(ana, List.of(months.get(4)), motherOfAna));
                jobs.add(() -> invoices.issue(bea, List.of(bBooks), motherOfBea));
                List<Result<InvoiceDocument>> results = runTogether(jobs);
                Set<String> numbers = results.stream().filter(Result::isSuccess).map(r -> r.getData().invoice().getNumber()).collect(Collectors.toCollection(TreeSet::new));
                return results.stream().allMatch(Result::isSuccess)
                        && numbers.equals(Set.of(Invoice.numberOf((int) start + 1), Invoice.numberOf((int) start + 2), Invoice.numberOf((int) start + 3)))
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == start + 3
                        && results.stream().allMatch(r -> Files.isRegularFile(r.getData().file()));
            });

            // ---- the file can fail, the invoice does not ----
            Path blocker = Files.writeString(folder.resolve("bloqueo.txt"), "x");
            InvoiceController brokenDisk = new InvoiceController(daos.invoiceDao(), daos.paymentDao(), daos.studentDao(), () -> profile,
                    () -> new InvoiceFileStore(blocker.resolve("sub")), clock::get, logger);
            Payment lastOne = months.get(5);
            Result<InvoiceDocument> saved = brokenDisk.issue(ana, List.of(lastOne), motherOfAna);
            check("If the TXT cannot be written the invoice is still saved, the user is told, and the problem is logged", () -> {
                String log = Files.readString(logger.getLogFile(), StandardCharsets.UTF_8);
                return saved.isSuccess() && saved.getData().file() == null && saved.getMessage().contains("No se pudo guardar el archivo")
                        && saved.getMessage().contains("La factura quedó registrada")
                        && scalar("SELECT COUNT(*) FROM facturas WHERE numero_factura = '" + saved.getData().invoice().getNumber() + "'") == 1
                        && log.contains("Write invoice file " + saved.getData().invoice().getFileName() + " failed");
            });
            String savedNumber = saved.getData().invoice().getNumber();
            check("The file can be generated again: it gets exactly the same text, and a broken disk is reported as an error", () -> {
                Path file = first.getData().file();
                Files.delete(file);
                Result<InvoiceDocument> again = invoices.regenerateFile(firstNumber);
                Result<InvoiceDocument> lost = invoices.regenerateFile(savedNumber);
                Result<InvoiceDocument> broken = brokenDisk.regenerateFile(firstNumber);
                return again.isSuccess() && Files.isRegularFile(file) && Files.readAllLines(file, StandardCharsets.UTF_8).equals(first.getData().text())
                        && lost.isSuccess() && Files.isRegularFile(folder.resolve(saved.getData().invoice().getFileName()))
                        && !broken.isSuccess() && broken.getMessage().contains("No se pudo guardar")
                        && !invoices.regenerateFile("FE-999999").isSuccess() && invoices.regenerateFile("FE-999999").getMessage().contains("No existe")
                        && !invoices.regenerateFile("abc").isSuccess();
            });
            check("An invoice is read back from the database exactly as it was issued, and its CUFE still verifies", () -> {
                Invoice loaded = daos.invoiceDao().findByNumber(firstNumber).orElseThrow();
                Invoice original = first.getData().invoice();
                return loaded.getId().equals(original.getId()) && loaded.getNumber().equals(firstNumber) && loaded.getIssuedAt().equals(NOW)
                        && loaded.getStudent().getId().equals(ana.getId()) && loaded.getClient().getDocumentNumber().equals("99810000000")
                        && loaded.getClient().getAddress().equals("Calle 1 # 2-3, Bogotá")
                        && loaded.getLines().size() == 4 && loaded.getTotal().compareTo(original.getTotal()) == 0
                        && loaded.getLines().get(1).getUnitValue().compareTo(new BigDecimal("840.34")) == 0
                        && loaded.getLines().get(3).getDescription().equals("Cuota Zzfact - cuota de febrero 2026")
                        && loaded.getLines().get(0).getReceiptNumber().equals(aBooks.getReceiptNumber())
                        && loaded.getCufe().equals(original.getCufe()) && CufeGenerator.generate(loaded, profile).equals(loaded.getCufe())
                        && loaded.getResolution().equals(profile.resolutionText())
                        && InvoiceTextRenderer.render(loaded, profile).equals(first.getData().text());
            });
            check("View an invoice by number (15, FE-000015...); an unknown or badly typed number is an error", () -> {
                Result<InvoiceDocument> byShort = invoices.view(String.valueOf(Invoice.sequenceOf(firstNumber)));
                Result<InvoiceDocument> byFull = invoices.view(firstNumber.toLowerCase());
                return byShort.isSuccess() && byShort.getData().text().equals(first.getData().text()) && byFull.isSuccess()
                        && !invoices.view("999999").isSuccess() && invoices.view("999999").getMessage().contains("No existe la factura FE-999999")
                        && !invoices.view("xyz").isSuccess();
            });
            check("The invoices of a student are listed newest first, each student his own", () -> {
                List<Invoice> mine = invoices.invoicesOf(ana).getData();
                return mine.size() >= 6 && mine.get(0).getSequence() > mine.get(mine.size() - 1).getSequence()
                        && mine.stream().allMatch(i -> i.getStudent().getId().equals(ana.getId()))
                        && mine.stream().sorted(Comparator.comparingInt(Invoice::getSequence).reversed()).toList().equals(mine)
                        && invoices.invoicesOf(nobody).getData().isEmpty()
                        && invoices.invoicesOf(bea).getData().size() == 1;
            });

            check("A wrong billing key in config.txt is an error message of the operation (not a crash), and nothing is saved", () -> {
                InvoiceController badConfig = new InvoiceController(daos.invoiceDao(), daos.paymentDao(), daos.studentDao(),
                        () -> BillingProfile.from((k, d) -> k.equals("billing.nit") ? "123" : d), () -> new InvoiceFileStore(folder), clock::get, logger);
                long counter = scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'");
                Result<InvoiceDocument> bad = badConfig.view(firstNumber);
                Result<InvoiceDocument> badIssue = badConfig.issue(bea, List.of(bBooks), motherOfBea);
                return !bad.isSuccess() && bad.getMessage().contains("billing.nit") && !badIssue.isSuccess() && badIssue.getMessage().contains("billing.nit")
                        && scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'") == counter;
            });

            // ---- the database protects itself ----
            check("The database refuses an invoice whose total does not add up, a payment in two invoices, and deleting an invoiced payment", () -> {
                String insert = "INSERT INTO facturas (numero_factura, id_estudiante, id_acudiente, fecha_emision, subtotal, valor_iva, total, cufe, resolucion, nombre_archivo) "
                        + "SELECT 'FE-999999', " + ana.getId() + ", " + motherOfAna.getId() + ", NOW(), 100, 19, 200, REPEAT('a', 96), 'x', 'factura_999999.txt'";
                String twice = "INSERT INTO detalle_factura (id_factura, id_pago, descripcion, cantidad, valor_unitario, porcentaje_iva, subtotal, valor_iva, total) "
                        + "SELECT id_factura, " + aBooks.getId() + ", 'otra vez', 1, 100, 0, 100, 0, 100 FROM facturas WHERE numero_factura = '" + savedNumber + "'";
                String inconsistentLine = "INSERT INTO detalle_factura (id_factura, id_pago, descripcion, cantidad, valor_unitario, porcentaje_iva, subtotal, valor_iva, total) "
                        + "SELECT id_factura, " + bBooks.getId() + ", 'mal', 2, 100, 0, 100, 0, 100 FROM facturas WHERE numero_factura = '" + savedNumber + "'";
                return fails(insert) && fails(twice) && fails(inconsistentLine) && fails("DELETE FROM pagos WHERE id_pago = " + aBooks.getId())
                        && fails("DELETE FROM acudientes WHERE id_acudiente = " + motherOfAna.getId())
                        && scalar("SELECT COUNT(*) FROM facturas WHERE numero_factura = 'FE-999999'") == 0;
            });

            // ---- sample data and counters ----
            check("The sample database leaves the invoice counter at the last invoice that exists", () ->
                    scalar("SELECT ultimo_numero FROM consecutivos WHERE tipo = 'Factura'")
                            == scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(numero_factura, 4) AS UNSIGNED)), 0) FROM facturas"));
        } finally {
            cleanUp();
            deleteFolder(folder);
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    /** Runs the jobs at the same moment (all wait for each other at a barrier before starting). */
    private static <T> List<T> runTogether(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            CyclicBarrier barrier = new CyclicBarrier(jobs.size());
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return job.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private static boolean invalidProfile(String key, String value) {
        BiFunction<String, String, String> config = (k, d) -> k.equals(key) ? value : d;
        try {
            BillingProfile.from(config);
            return false;
        } catch (ConfigurationException e) {
            return e.getMessage().contains(key);
        }
    }

    private static boolean invalidRange(String from, String to) {
        try {
            BillingProfile.from((k, d) -> k.equals("billing.resolution.from") ? from : k.equals("billing.resolution.to") ? to : d);
            return false;
        } catch (ConfigurationException e) {
            return e.getMessage().contains("billing.resolution");
        }
    }

    private static Invoice copy(Student student, Guardian client, LocalDateTime at, List<InvoiceLine> lines, int sequence) {
        Invoice invoice = new Invoice(student, client, at, lines);
        invoice.setSequence(sequence);
        return invoice;
    }

    /** A payment that exists only in memory (with the id and receipt a saved one would have). */
    private static Payment fakePayment(Student student, String conceptName, int vat, String amount, Integer month, int id) {
        PaymentConcept c = concept("ZZFACT_X" + id, conceptName, 1000000, vat, month != null);
        Payment payment = new Payment(student, c, null, new BigDecimal(amount), PAID_ON, PaymentMethod.CASH, 2026, month);
        payment.setId(id);
        payment.setReceiptNumber(String.format("REC-%06d", id));
        return payment;
    }

    private static Payment pay(PaymentController payments, Student student, PaymentConcept concept, Guardian payer, PaymentMethod method, Integer month) {
        Result<Payment> result = payments.register(student, concept, payer, null, PAID_ON, method, 2026, month);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Could not prepare the payment: " + result.getMessage());
        }
        return result.getData();
    }

    private static Payment payCents(PaymentController payments, Student student, PaymentConcept concept, Guardian payer, String amount) {
        Result<Payment> result = payments.register(student, concept, payer, new BigDecimal(amount), PAID_ON, PaymentMethod.CARD, 2026, null);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Could not prepare the payment: " + result.getMessage());
        }
        return result.getData();
    }

    private static PaymentConcept concept(String code, String name, long amount, int vat, boolean recurring) {
        return new PaymentConcept(code, name, null, BigDecimal.valueOf(amount), vat, recurring);
    }

    private static Student student(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2012, 5, 20)).admissionDate(LocalDate.now().minusYears(1)).build();
    }

    private static Guardian guardian(String document, String firstName, String address) {
        Guardian guardian = new Guardian(DocumentType.CC, document, firstName, "Prueba", "Madre", "3001234567");
        guardian.setAddress(address);
        return guardian;
    }

    private static long scalar(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static boolean fails(String sql) {
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
     * Deletes what this check created, in the order the foreign keys need (lines, invoices, payments, students,
     * guardians, concepts), and puts the receipt and invoice counters back to the last number that still exists.
     */
    private static void cleanUp() throws SQLException, DaoException {
        String mine = "(SELECT id_estudiante FROM estudiantes WHERE numero_documento LIKE '9980%')";
        execute("DELETE FROM detalle_factura WHERE id_factura IN (SELECT id_factura FROM facturas WHERE id_estudiante IN " + mine + ")");
        execute("DELETE FROM facturas WHERE id_estudiante IN " + mine + " OR numero_factura = 'FE-999999'");
        execute("DELETE FROM pagos WHERE id_estudiante IN " + mine);
        execute("DELETE FROM estudiantes WHERE numero_documento LIKE '9980%'");
        execute("DELETE FROM acudientes WHERE numero_documento LIKE '9981%'");
        execute("DELETE FROM pagos WHERE id_concepto IN (SELECT id_concepto FROM conceptos_pago WHERE codigo_concepto LIKE 'ZZFACT%')");
        execute("DELETE FROM conceptos_pago WHERE codigo_concepto LIKE 'ZZFACT%'");
        execute("UPDATE consecutivos SET ultimo_numero = (SELECT COALESCE(MAX(CAST(SUBSTRING(numero_recibo, 5) AS UNSIGNED)), 0) "
                + "FROM pagos WHERE numero_recibo LIKE 'REC-%') WHERE tipo = 'Recibo'");
        execute("UPDATE consecutivos SET ultimo_numero = (SELECT COALESCE(MAX(CAST(SUBSTRING(numero_factura, 4) AS UNSIGNED)), 0) "
                + "FROM facturas) WHERE tipo = 'Factura'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private static void deleteFolder(Path folder) throws Exception {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
