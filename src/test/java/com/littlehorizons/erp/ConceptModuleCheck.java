package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.StudentPriceController;
import com.littlehorizons.erp.controller.finance.ConceptAnalyzer;
import com.littlehorizons.erp.controller.finance.ConceptCatalog;
import com.littlehorizons.erp.controller.finance.PriceLine;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.model.enums.DocumentType;
import com.littlehorizons.erp.model.enums.Gender;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Manual check of module 3.1 (plain Java). It creates its own students (documents starting with
 * 9970), guardians (9971) and concepts with the code ZZPRUEBA_... and deletes everything at the
 * end. The last checks read the sample data of docs/database.sql. Run main() from IntelliJ with
 * the database loaded and config.txt in place.
 */
public final class ConceptModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ErrorLogger logger = ErrorLogger.getInstance();
        StudentController students = new StudentController(daos.studentDao(), logger);
        PaymentConceptController concepts = new PaymentConceptController(daos.paymentConceptDao(), logger);
        StudentPriceController prices = new StudentPriceController(daos.studentConceptPriceDao(), daos.paymentConceptDao(), logger);
        int year = LocalDate.now().getYear();

        try {
            cleanUp();

            // ---- money ----
            check("Money.parse accepts digits with up to two decimals and nothing else", () ->
                    Money.parse("650000").equals(new BigDecimal("650000.00"))
                            && Money.parse("650000.5").equals(new BigDecimal("650000.50"))
                            && throwsValidation(() -> Money.parse("1.500.000"))
                            && throwsValidation(() -> Money.parse("650,5"))
                            && throwsValidation(() -> Money.parse("abc"))
                            && throwsValidation(() -> Money.parse("-5"))
                            && throwsValidation(() -> Money.parse("10.123"))
                            && throwsValidation(() -> Money.parse("  ")));
            check("Money.format uses dots for thousands and shows cents only when there are some", () ->
                    Money.format(new BigDecimal("1500000.00")).equals("$1.500.000")
                            && Money.format(new BigDecimal("650000.50")).equals("$650.000,50")
                            && Money.format(BigDecimal.ZERO).equals("$0")
                            && Money.format(new BigDecimal("999.00")).equals("$999"));

            // ---- entities and the pure calculations ----
            check("A concept needs a valid code, a letters-only name and a value above zero", () ->
                    throwsValidation(() -> concept("1AB", "Concepto Zzprueba", 100))
                            && throwsValidation(() -> concept("AB", "Concepto Zzprueba", 100))
                            && throwsValidation(() -> concept("AB CD", "Concepto Zzprueba", 100))
                            && throwsValidation(() -> concept("", "Concepto Zzprueba", 100))
                            && throwsValidation(() -> concept("ZZPRUEBA_X", "Concepto 2026", 100))
                            && throwsValidation(() -> concept("ZZPRUEBA_X", "  ", 100))
                            && throwsValidation(() -> concept("ZZPRUEBA_X", "Concepto Zzprueba", 0))
                            && throwsValidation(() -> concept("ZZPRUEBA_X", "Concepto Zzprueba", -5))
                            && throwsValidation(() -> concept("ZZPRUEBA_X", "Concepto Zzprueba", 50000001))
                            && concept("zzprueba_x", "Concepto Zzprueba", 50000000).getCode().equals("ZZPRUEBA_X"));
            check("The value admits two decimals at most, the VAT goes from 0 to 100, the description is optional", () ->
                    throwsValidation(() -> new PaymentConcept("ZZPRUEBA_X", "Concepto Zzprueba", null, new BigDecimal("10.123"), 0, false))
                            && throwsValidation(() -> new PaymentConcept("ZZPRUEBA_X", "Concepto Zzprueba", null, BigDecimal.TEN, 101, false))
                            && throwsValidation(() -> new PaymentConcept("ZZPRUEBA_X", "Concepto Zzprueba", null, BigDecimal.TEN, -1, false))
                            && throwsValidation(() -> new PaymentConcept("ZZPRUEBA_X", "Concepto Zzprueba", "x".repeat(501), BigDecimal.TEN, 0, false))
                            && new PaymentConcept("ZZPRUEBA_X", "Concepto Zzprueba", "  ", BigDecimal.TEN, 19, true).getDescription() == null);
            Student probe = student("99700000009", "Probe");
            PaymentConcept probeConcept = concept("ZZPRUEBA_P", "Concepto Zzprueba Prueba", 650000);
            check("A personalized value is between zero and the base value and needs a reason and a valid year", () ->
                    throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 2026, new BigDecimal("650000.01"), "Beca"))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 2026, new BigDecimal("-1"), "Beca"))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 2026, new BigDecimal("1000.001"), "Beca"))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 2026, BigDecimal.TEN, "  "))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 2026, BigDecimal.TEN, "x".repeat(151)))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, 1999, BigDecimal.TEN, "Beca"))
                            && throwsValidation(() -> new StudentConceptPrice(probe, probeConcept, year + 2, BigDecimal.TEN, "Beca"))
                            && new StudentConceptPrice(probe, probeConcept, 2026, BigDecimal.ZERO, "Beca total").getAmount().signum() == 0
                            && new StudentConceptPrice(probe, probeConcept, 2026, probeConcept.getBaseAmount(), "Igual").getAmount().equals(new BigDecimal("650000.00")));
            check("The discount percent is calculated from the base value", () ->
                    new PriceLine(probeConcept, new BigDecimal("585000.00"), "x").discountPercent().compareTo(new BigDecimal("10.0")) == 0
                            && new PriceLine(probeConcept, BigDecimal.ZERO, "x").discountPercent().compareTo(new BigDecimal("100.0")) == 0
                            && new PriceLine(probeConcept, new BigDecimal("650000.00"), "x").discountPercent().signum() == 0
                            && new PriceLine(probeConcept, new BigDecimal("500000.00"), "x").discountPercent().compareTo(new BigDecimal("23.1")) == 0);
            check("The catalog is sorted (active first, Spanish alphabetical) and totalled with streams", () -> {
                PaymentConcept a = withId(concept("ZZPRUEBA_A", "Zeta", 100), 1, true);
                PaymentConcept b = withId(concept("ZZPRUEBA_B", "Álamo", 100), 2, true);
                PaymentConcept c = withId(concept("ZZPRUEBA_C", "Antiguo", 100), 3, false);
                PaymentConcept d = withId(concept("ZZPRUEBA_D", "Beta", 100), 4, true);
                d.setRecurring(true);
                ConceptCatalog catalog = ConceptAnalyzer.catalog(List.of(a, c, d, b), Map.of(1, 3, 4, 1));
                List<String> names = catalog.entries().stream().map(e -> e.concept().getName()).toList();
                var s = catalog.summary();
                return names.equals(List.of("Álamo", "Beta", "Zeta", "Antiguo"))
                        && s.total() == 4 && s.active() == 3 && s.inactive() == 1 && s.recurring() == 1
                        && catalog.entries().get(2).personalizedStudents() == 3 && catalog.entries().get(0).personalizedStudents() == 0;
            });
            check("The price list shows only active concepts and applies the personalized values", () -> {
                PaymentConcept a = withId(concept("ZZPRUEBA_A", "Pensión", 650000), 1, true);
                PaymentConcept b = withId(concept("ZZPRUEBA_B", "Matrícula", 1200000), 2, true);
                PaymentConcept c = withId(concept("ZZPRUEBA_C", "Antiguo", 90000), 3, false);
                StudentConceptPrice own = new StudentConceptPrice(probe, a, 2026, new BigDecimal("585000"), "Hermanos");
                List<PriceLine> lines = ConceptAnalyzer.priceList(List.of(a, b, c), List.of(own));
                return lines.size() == 2 && lines.get(0).concept() == b && !lines.get(0).isPersonalized()
                        && lines.get(0).effectiveAmount().equals(new BigDecimal("1200000.00"))
                        && lines.get(1).isPersonalized() && lines.get(1).reason().equals("Hermanos")
                        && lines.get(1).effectiveAmount().equals(new BigDecimal("585000.00"))
                        && ConceptAnalyzer.lineFor(a, Optional.empty()).effectiveAmount().equals(new BigDecimal("650000.00"));
            });

            // ---- catalog in the database ----
            PaymentConcept tuition = new PaymentConcept("ZZPRUEBA_PENSION", "Concepto Zzprueba Pension", "Pensión de prueba",
                    new BigDecimal("650000"), 0, true);
            check("Create a concept", () -> concepts.create(tuition).isSuccess() && tuition.getId() != null);
            check("A concept code cannot be repeated", () -> {
                Result<PaymentConcept> r = concepts.create(new PaymentConcept("zzprueba_pension", "Concepto Zzprueba Otro", null, BigDecimal.TEN, 0, false));
                return !r.isSuccess() && r.getMessage().contains("código");
            });
            check("A concept name cannot be repeated (even in other case or without accents)", () -> {
                Result<PaymentConcept> r = concepts.create(new PaymentConcept("ZZPRUEBA_OTRO", "concepto zzprueba pensión", null, BigDecimal.TEN, 0, false));
                return !r.isSuccess() && r.getMessage().contains("llamado");
            });
            check("The code and name checks ignore the concept being edited", () ->
                    concepts.codeTaken("zzprueba_pension", null).getData()
                            && !concepts.codeTaken("ZZPRUEBA_PENSION", tuition.getId()).getData()
                            && concepts.nameTaken("Concepto Zzprueba Pension", null).getData()
                            && !concepts.nameTaken("Concepto Zzprueba Pension", tuition.getId()).getData()
                            && !concepts.nameTaken("Concepto Zzprueba Libre", null).getData());
            PaymentConcept uniform = new PaymentConcept("ZZPRUEBA_UNIFORME", "Concepto Zzprueba Uniforme", null,
                    new BigDecimal("185000.50"), 19, false);
            check("Create a second concept with VAT and cents; the cents and the VAT are saved", () -> {
                if (!concepts.create(uniform).isSuccess()) {
                    return false;
                }
                PaymentConcept saved = daos.paymentConceptDao().findById(uniform.getId()).orElseThrow();
                return saved.getBaseAmount().equals(new BigDecimal("185000.50")) && saved.getVatPercent() == 19
                        && !saved.isRecurring() && saved.getDescription() == null && saved.isActive();
            });
            check("Edit a concept: the new data is saved and the id and state are kept", () -> {
                PaymentConcept changed = new PaymentConcept("ZZPRUEBA_UNIFORME", "Concepto Zzprueba Uniforme Diario",
                        "Uniforme completo", new BigDecimal("190000"), 19, false);
                changed.setId(uniform.getId());
                changed.setActive(uniform.isActive());
                PaymentConcept saved = concepts.update(changed).isSuccess()
                        ? daos.paymentConceptDao().findById(uniform.getId()).orElseThrow() : null;
                return saved != null && saved.getName().equals("Concepto Zzprueba Uniforme Diario")
                        && saved.getBaseAmount().equals(new BigDecimal("190000.00")) && "Uniforme completo".equals(saved.getDescription());
            });
            check("An edit cannot take the code or the name of another concept", () -> {
                PaymentConcept clash = new PaymentConcept("ZZPRUEBA_PENSION", "Concepto Zzprueba Uniforme Diario", null, BigDecimal.TEN, 0, false);
                clash.setId(uniform.getId());
                PaymentConcept clash2 = new PaymentConcept("ZZPRUEBA_UNIFORME", "Concepto Zzprueba Pension", null, BigDecimal.TEN, 0, false);
                clash2.setId(uniform.getId());
                return !concepts.update(clash).isSuccess() && !concepts.update(clash2).isSuccess();
            });
            PaymentConcept oldConcept = new PaymentConcept("ZZPRUEBA_ANTIGUO", "Concepto Zzprueba Antiguo", null, new BigDecimal("90000"), 0, false);
            check("A concept can be deactivated and activated; doing it twice is refused and the object stays coherent", () -> {
                concepts.create(oldConcept);
                boolean off = concepts.setActive(oldConcept, false).isSuccess() && !oldConcept.isActive()
                        && !daos.paymentConceptDao().findById(oldConcept.getId()).orElseThrow().isActive();
                Result<PaymentConcept> again = concepts.setActive(oldConcept, false);
                boolean on = concepts.setActive(oldConcept, true).isSuccess() && oldConcept.isActive();
                boolean off2 = concepts.setActive(oldConcept, false).isSuccess();
                return off && !again.isSuccess() && again.getMessage().contains("inactivo") && on && off2;
            });
            check("Search finds concepts by code and by name; the active search skips the inactive ones", () ->
                    concepts.search("zzprueba_pension").getData().size() == 1
                            && concepts.search("uniforme diario").getData().size() == 1
                            && concepts.search("ZZPRUEBA").getData().size() == 3
                            && concepts.searchActive("ZZPRUEBA").getData().size() == 2
                            && !concepts.search("  ").isSuccess()
                            && concepts.search("100%").getData().isEmpty());

            // ---- personalized values ----
            List<Student> pupils = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Student s = student("9970000000" + i, "Alumno" + (char) ('A' + i));
                pupils.add(s);
                students.register(s, new ArrayList<>(List.of(new StudentGuardian(
                        new Guardian(DocumentType.CC, "9971000000" + i, "Madre", "Prueba", "Madre", "3001234567"), true, true))));
            }
            Student retired = pupils.get(4);
            check("Register five students; one is retired", () -> pupils.stream().allMatch(s -> s.getId() != null) && students.retire(retired).isSuccess());
            check("Assign a personalized value (a discount) to an active student", () -> {
                Result<StudentConceptPrice> r = prices.assign(new StudentConceptPrice(pupils.get(0), tuition, year,
                        new BigDecimal("585000"), "Descuento por hermanos"));
                return r.isSuccess() && r.getMessage().contains("asignado") && r.getMessage().contains("$585.000")
                        && r.getMessage().contains("10% menos");
            });
            check("The value is saved and read back with its reason", () -> {
                StudentConceptPrice saved = daos.studentConceptPriceDao().find(pupils.get(0), tuition, year).orElseThrow();
                return saved.getAmount().equals(new BigDecimal("585000.00")) && saved.getReason().equals("Descuento por hermanos")
                        && saved.getConcept().getCode().equals("ZZPRUEBA_PENSION") && saved.getStudent() == pupils.get(0);
            });
            check("Assigning again replaces the value (one row only)", () -> {
                Result<StudentConceptPrice> r = prices.assign(new StudentConceptPrice(pupils.get(0), tuition, year,
                        new BigDecimal("520000"), "Beca parcial"));
                return r.isSuccess() && r.getMessage().contains("actualizado")
                        && scalar("SELECT COUNT(*) FROM valores_concepto_estudiante v JOIN estudiantes e USING(id_estudiante) "
                        + "WHERE e.numero_documento = '99700000001' AND v.id_concepto = " + tuition.getId()) == 1
                        && daos.studentConceptPriceDao().find(pupils.get(0), tuition, year).orElseThrow().getReason().equals("Beca parcial");
            });
            check("A full scholarship (zero) is accepted", () ->
                    prices.assign(new StudentConceptPrice(pupils.get(1), tuition, year, BigDecimal.ZERO, "Beca total")).isSuccess()
                            && prices.priceOf(pupils.get(1), tuition, year).getData().discountPercent().compareTo(new BigDecimal("100.0")) == 0);
            check("A retired student cannot have a personalized value", () -> {
                Result<StudentConceptPrice> r = prices.assign(new StudentConceptPrice(retired, tuition, year, BigDecimal.TEN, "Beca"));
                return !r.isSuccess() && r.getMessage().contains("activos");
            });
            check("An inactive concept cannot be personalized", () -> {
                Result<StudentConceptPrice> r = prices.assign(new StudentConceptPrice(pupils.get(0), oldConcept, year, BigDecimal.TEN, "Beca"));
                return !r.isSuccess() && r.getMessage().contains("inactivo");
            });
            check("If the base value went down after the user picked the concept, the old amount is refused", () -> {
                PaymentConcept stale = daos.paymentConceptDao().findById(uniform.getId()).orElseThrow();
                StudentConceptPrice price = new StudentConceptPrice(pupils.get(2), stale, year, new BigDecimal("150000"), "Descuento");
                PaymentConcept lowered = new PaymentConcept("ZZPRUEBA_UNIFORME", "Concepto Zzprueba Uniforme Diario", null,
                        new BigDecimal("120000"), 19, false);
                lowered.setId(uniform.getId());
                Result<StudentConceptPrice> r = concepts.update(lowered).isSuccess() ? prices.assign(price) : null;
                return r != null && !r.isSuccess() && r.getMessage().contains("no puede superar");
            });
            check("The same student can have values for different years and concepts", () ->
                    prices.assign(new StudentConceptPrice(pupils.get(0), tuition, year - 1, new BigDecimal("500000"), "Año anterior")).isSuccess()
                            && prices.assign(new StudentConceptPrice(pupils.get(0), uniform, year, new BigDecimal("100000"), "Fondo solidario")).isSuccess()
                            && scalar("SELECT COUNT(*) FROM valores_concepto_estudiante v JOIN estudiantes e USING(id_estudiante) "
                            + "WHERE e.numero_documento = '99700000001'") == 3);
            check("The price list of the student: personalized lines, base lines, and the inactive concept hidden", () -> {
                List<PriceLine> lines = prices.priceList(pupils.get(0), year).getData();
                PriceLine pension = lines.stream().filter(l -> l.concept().getCode().equals("ZZPRUEBA_PENSION")).findFirst().orElseThrow();
                PriceLine unif = lines.stream().filter(l -> l.concept().getCode().equals("ZZPRUEBA_UNIFORME")).findFirst().orElseThrow();
                PriceLine base = lines.stream().filter(l -> l.concept().getCode().equals("MATRICULA_ANUAL")).findFirst().orElseThrow();
                return pension.effectiveAmount().equals(new BigDecimal("520000.00")) && pension.isPersonalized()
                        && unif.effectiveAmount().equals(new BigDecimal("100000.00"))
                        && !base.isPersonalized() && base.effectiveAmount().equals(base.baseAmount())
                        && lines.stream().noneMatch(l -> l.concept().getCode().equals("ZZPRUEBA_ANTIGUO"));
            });
            check("Another year shows its own value, and a student without values pays the base value", () ->
                    prices.priceOf(pupils.get(0), tuition, year - 1).getData().effectiveAmount().equals(new BigDecimal("500000.00"))
                            && !prices.priceOf(pupils.get(3), tuition, year).getData().isPersonalized()
                            && prices.priceOf(pupils.get(3), tuition, year).getData().effectiveAmount().equals(new BigDecimal("650000.00")));
            check("The catalog counts the students with a personalized value per concept", () -> {
                ConceptCatalog catalog = concepts.catalog().getData();
                int pension = catalog.entries().stream().filter(e -> e.concept().getCode().equals("ZZPRUEBA_PENSION"))
                        .findFirst().orElseThrow().personalizedStudents();
                return pension == 3 && catalog.summary().total() == catalog.entries().size()
                        && catalog.entries().stream().filter(e -> !e.concept().isActive()).allMatch(e -> catalog.entries().indexOf(e) >= catalog.summary().active());
            });
            check("Remove a personalized value; removing it twice says there is none", () -> {
                Result<Void> first = prices.remove(pupils.get(1), tuition, year);
                Result<Void> second = prices.remove(pupils.get(1), tuition, year);
                return first.isSuccess() && first.getMessage().contains("$650.000")
                        && !second.isSuccess() && second.getMessage().contains("no tiene un valor personalizado")
                        && !prices.priceOf(pupils.get(1), tuition, year).getData().isPersonalized();
            });
            check("Deleting a student removes his or her personalized values (database cascade)", () -> {
                long before = scalar("SELECT COUNT(*) FROM valores_concepto_estudiante");
                execute("DELETE FROM estudiantes WHERE numero_documento = '99700000001'");
                return scalar("SELECT COUNT(*) FROM valores_concepto_estudiante") == before - 3;
            });
            check("The database CHECK stops a negative personalized value", () ->
                    translatedFailure("INSERT INTO valores_concepto_estudiante (id_estudiante, id_concepto, anio_academico, monto_ajustado, motivo) "
                            + "VALUES (" + pupils.get(2).getId() + ", " + tuition.getId() + ", " + year + ", -1, 'x')"));

            // ---- the sample data of docs/database.sql ----
            check("The sample data has 12 concepts (11 active) and at least 20 personalized values", () ->
                    scalar("SELECT COUNT(*) FROM conceptos_pago WHERE codigo_concepto NOT LIKE 'ZZPRUEBA%'") == 12
                            && scalar("SELECT COUNT(*) FROM conceptos_pago WHERE esta_activo = 1 AND codigo_concepto NOT LIKE 'ZZPRUEBA%'") == 11
                            && scalar("SELECT COUNT(*) FROM valores_concepto_estudiante v JOIN estudiantes e USING(id_estudiante) WHERE e.numero_documento NOT LIKE '9970%'") >= 20);
            check("Sample: every concept is read back through the entity (names, values and VAT are valid)", () ->
                    daos.paymentConceptDao().findAll().stream().filter(c -> !c.getCode().startsWith("ZZPRUEBA")).count() == 12
                            && daos.paymentConceptDao().findAll().stream().anyMatch(c -> c.getCode().equals("PENSION_MENSUAL")
                            && c.getBaseAmount().equals(new BigDecimal("650000.00")) && c.isRecurring() && c.getVatPercent() == 0)
                            && daos.paymentConceptDao().findAll().stream().anyMatch(c -> c.getCode().equals("UNIFORME_DIARIO") && c.getVatPercent() == 19));
            check("Sample: no personalized value is above the base value or for an inactive concept", () ->
                    scalar("SELECT COUNT(*) FROM valores_concepto_estudiante v JOIN conceptos_pago c USING(id_concepto) "
                            + "WHERE v.monto_ajustado > c.monto_base OR (c.esta_activo = 0 AND v.anio_academico >= " + year + ")") == 0);
            check("Sample: the siblings 37 to 40 pay 10% less for the 2026 tuition", () -> {
                Student sibling = daos.studentDao().findById(37).orElseThrow();
                PaymentConcept pension = daos.paymentConceptDao().search("PENSION_MENSUAL").get(0);
                PriceLine line = prices.priceOf(sibling, pension, 2026).getData();
                return line.effectiveAmount().equals(new BigDecimal("585000.00")) && line.isPersonalized()
                        && line.discountPercent().compareTo(new BigDecimal("10.0")) == 0
                        && scalar("SELECT COUNT(*) FROM valores_concepto_estudiante WHERE id_estudiante IN (37,38,39,40) "
                        + "AND anio_academico = 2026 AND motivo = 'Descuento por hermanos' AND monto_ajustado = 585000") == 4;
            });
            check("Sample: student 7 has three full scholarships (value zero)", () ->
                    scalar("SELECT COUNT(*) FROM valores_concepto_estudiante WHERE id_estudiante = 7 AND anio_academico = 2026 AND monto_ajustado = 0") == 3);
            check("Sample: the inactive concept «Plataforma virtual» is not offered to be personalized", () ->
                    concepts.searchActive("plataforma").getData().isEmpty() && concepts.search("plataforma").getData().size() == 1);
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static PaymentConcept concept(String code, String name, long amount) {
        return new PaymentConcept(code, name, null, BigDecimal.valueOf(amount), 0, false);
    }

    private static PaymentConcept withId(PaymentConcept concept, int id, boolean active) {
        concept.setId(id);
        concept.setActive(active);
        return concept;
    }

    private static Student student(String document, String firstName) {
        return Student.builder().documentType(DocumentType.TI).documentNumber(document)
                .firstName(firstName).lastName("Prueba").gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2012, 5, 20)).admissionDate(LocalDate.now().minusYears(1)).build();
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

    /** Deletes what this check created: the students first (their values go with them), then the concepts. */
    private static void cleanUp() throws SQLException, DaoException {
        execute("DELETE FROM estudiantes WHERE numero_documento LIKE '9970%'");
        execute("DELETE FROM acudientes WHERE numero_documento LIKE '9971%'");
        execute("DELETE FROM conceptos_pago WHERE codigo_concepto LIKE 'ZZPRUEBA%'");
    }

    private static void execute(String sql) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
