package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.resource.ResourceAnalyzer;
import com.littlehorizons.erp.controller.resource.ResourceInventory;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.DaoFactory;
import com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection;
import com.littlehorizons.erp.model.dao.jdbc.JdbcDaoFactory;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Manual check of module 4.1 (plain Java). It creates its own resources with codes that start with
 * ZZP- and deletes everything at the end (also the loans it inserts, which go with the resource).
 * The last checks read the sample data of docs/database.sql. Run main() from IntelliJ with the
 * database loaded and config.txt in place.
 */
public final class ResourceModuleCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        DaoFactory daos = new JdbcDaoFactory(DatabaseConnection.getInstance());
        ResourceController resources = new ResourceController(daos.resourceDao(), ErrorLogger.getInstance());

        try {
            cleanUp();

            // ---- the entity ----
            check("The code is upper-cased and must have 3 to 20 letters or digits separated by single hyphens", () ->
                    resource("lib-001", "Libro").getCode().equals("LIB-001")
                            && resource("  tec-12  ", "Libro").getCode().equals("TEC-12")
                            && resource("ABC", "Libro").getCode().equals("ABC")
                            && throwsValidation(() -> resource("", "Libro"))
                            && throwsValidation(() -> resource("AB", "Libro"))
                            && throwsValidation(() -> resource("LIB 001", "Libro"))
                            && throwsValidation(() -> resource("LIB--001", "Libro"))
                            && throwsValidation(() -> resource("-LIB-001", "Libro"))
                            && throwsValidation(() -> resource("LIB-001-", "Libro"))
                            && throwsValidation(() -> resource("LIB_001", "Libro"))
                            && throwsValidation(() -> resource("A".repeat(21), "Libro"))
                            && resource("A".repeat(20), "Libro").getCode().length() == 20);
            check("The name and the location start with a letter and may contain numbers (a model, a shelf)", () ->
                    resource("ZZP-N1", "Tablet Samsung Galaxy Tab A9").getName().equals("Tablet Samsung Galaxy Tab A9")
                            && resource("ZZP-N1", "Calculadora científica Casio fx-991LA").getName().contains("fx-991LA")
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, null, "Biblioteca - Estante A1")
                            .getLocation().equals("Biblioteca - Estante A1")
                            && throwsValidation(() -> resource("ZZP-N1", "9 Tablets"))
                            && throwsValidation(() -> resource("ZZP-N1", "Tablet @ casa"))
                            && throwsValidation(() -> resource("ZZP-N1", "   "))
                            && throwsValidation(() -> resource("ZZP-N1", "A".repeat(151)))
                            && resource("ZZP-N1", "A".repeat(150)).getName().length() == 150
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, null, "  "))
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, null, "1 Biblioteca"))
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, null, "A".repeat(101))));
            check("The category is required; the description is optional (blank = none) up to 500 characters", () ->
                    throwsValidation(() -> new Resource("ZZP-N1", "Libro", null, null, null, "Biblioteca"))
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, "   ", null, "Biblioteca").getDescription() == null
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, " Nuevo ", null, "Biblioteca").getDescription().equals("Nuevo")
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, "x".repeat(501), null, "Biblioteca"))
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, "x".repeat(500), null, "Biblioteca").getDescription().length() == 500);
            check("The acquisition date is optional, cannot be in the future and cannot be before 1900", () ->
                    new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, null, "Biblioteca").getAcquisitionDate() == null
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, LocalDate.now(), "Biblioteca").getAcquisitionDate() != null
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, LocalDate.now().plusDays(1), "Biblioteca"))
                            && throwsValidation(() -> new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, LocalDate.of(1899, 12, 31), "Biblioteca"))
                            && new Resource("ZZP-N1", "Libro", ResourceCategory.BOOK, null, LocalDate.of(1900, 1, 1), "Biblioteca").getAcquisitionDate() != null);
            check("A new Resource is Disponible and the state cannot be null", () -> {
                Resource r = resource("ZZP-N1", "Libro");
                return r.getStatus() == ResourceStatus.AVAILABLE && throwsValidation(() -> r.setStatus(null));
            });

            // ---- the enums ----
            check("The enum labels are exactly the ones of the database columns and can be found again", () ->
                    List.of(ResourceStatus.values()).stream().map(ResourceStatus::getDbLabel).toList()
                            .equals(List.of("Disponible", "Prestado", "En Mantenimiento", "Dado de Baja"))
                            && List.of(ResourceCategory.values()).stream().map(ResourceCategory::getDbLabel).toList()
                            .equals(List.of("Libro", "Equipo de Laboratorio", "Equipo Deportivo", "Dispositivo Tecnológico", "Material Didáctico"))
                            && ResourceStatus.fromDbLabel("En Mantenimiento") == ResourceStatus.MAINTENANCE
                            && ResourceCategory.fromDbLabel("Equipo Deportivo") == ResourceCategory.SPORTS_EQUIPMENT
                            && throwsIllegalArgument(() -> ResourceStatus.fromDbLabel("Perdido")));
            check("The state changes allowed by hand: Disponible and En Mantenimiento move between them or to baja; Prestado and Dado de Baja are closed", () ->
                    ResourceStatus.AVAILABLE.manualTargets().equals(EnumSet.of(ResourceStatus.MAINTENANCE, ResourceStatus.WRITTEN_OFF))
                            && ResourceStatus.MAINTENANCE.manualTargets().equals(EnumSet.of(ResourceStatus.AVAILABLE, ResourceStatus.WRITTEN_OFF))
                            && ResourceStatus.BORROWED.manualTargets().isEmpty()
                            && ResourceStatus.WRITTEN_OFF.manualTargets().isEmpty());

            // ---- the pure calculations (streams) ----
            check("The filter combines category and state; null means no filter", () -> {
                List<Resource> list = List.of(
                        withStatus(resource("ZZP-A1", "Libro uno"), ResourceCategory.BOOK, ResourceStatus.AVAILABLE),
                        withStatus(resource("ZZP-A2", "Libro dos"), ResourceCategory.BOOK, ResourceStatus.BORROWED),
                        withStatus(resource("ZZP-A3", "Tablet uno"), ResourceCategory.TECH_DEVICE, ResourceStatus.AVAILABLE),
                        withStatus(resource("ZZP-A4", "Tablet dos"), ResourceCategory.TECH_DEVICE, ResourceStatus.WRITTEN_OFF));
                return ResourceAnalyzer.filter(list, null, null).size() == 4
                        && ResourceAnalyzer.filter(list, ResourceCategory.BOOK, null).size() == 2
                        && ResourceAnalyzer.filter(list, null, ResourceStatus.AVAILABLE).size() == 2
                        && ResourceAnalyzer.filter(list, ResourceCategory.TECH_DEVICE, ResourceStatus.AVAILABLE)
                        .stream().map(Resource::getCode).toList().equals(List.of("ZZP-A3"))
                        && ResourceAnalyzer.filter(list, ResourceCategory.LAB_EQUIPMENT, null).isEmpty();
            });
            check("The totals count every state and category, with 0 for the ones that have none", () -> {
                List<Resource> list = List.of(
                        withStatus(resource("ZZP-A1", "Libro uno"), ResourceCategory.BOOK, ResourceStatus.AVAILABLE),
                        withStatus(resource("ZZP-A2", "Libro dos"), ResourceCategory.BOOK, ResourceStatus.BORROWED),
                        withStatus(resource("ZZP-A3", "Tablet uno"), ResourceCategory.TECH_DEVICE, ResourceStatus.AVAILABLE));
                ResourceInventory inventory = ResourceAnalyzer.inventory(list);
                return inventory.total() == 3
                        && inventory.byStatus().size() == 4 && inventory.byCategory().size() == 5
                        && inventory.byStatus().get(ResourceStatus.AVAILABLE) == 2L
                        && inventory.byStatus().get(ResourceStatus.BORROWED) == 1L
                        && inventory.byStatus().get(ResourceStatus.MAINTENANCE) == 0L
                        && inventory.byStatus().get(ResourceStatus.WRITTEN_OFF) == 0L
                        && inventory.byCategory().get(ResourceCategory.BOOK) == 2L
                        && inventory.byCategory().get(ResourceCategory.LAB_EQUIPMENT) == 0L
                        && inventory.byStatus().keySet().stream().toList().equals(List.of(ResourceStatus.values()))
                        && ResourceAnalyzer.inventory(List.of()).total() == 0;
            });

            // ---- registering ----
            Resource microscope = new Resource("ZZP-001", "Microscopio de prueba", ResourceCategory.LAB_EQUIPMENT, "Aumento 400x",
                    LocalDate.of(2024, 3, 15), "Laboratorio de Ciencias - Armario 9");
            check("Create a resource: it gets an id and is saved as Disponible with all its data", () -> {
                if (!resources.create(microscope).isSuccess() || microscope.getId() == null) {
                    return false;
                }
                Resource saved = daos.resourceDao().findById(microscope.getId()).orElseThrow();
                return saved.getCode().equals("ZZP-001") && saved.getName().equals("Microscopio de prueba")
                        && saved.getCategory() == ResourceCategory.LAB_EQUIPMENT && "Aumento 400x".equals(saved.getDescription())
                        && LocalDate.of(2024, 3, 15).equals(saved.getAcquisitionDate())
                        && saved.getLocation().equals("Laboratorio de Ciencias - Armario 9")
                        && saved.getStatus() == ResourceStatus.AVAILABLE;
            });
            check("A resource without description and without date is saved with NULL in both", () -> {
                Resource plain = new Resource("ZZP-002", "Libro de prueba", ResourceCategory.BOOK, null, null, "Biblioteca - Estante Z");
                if (!resources.create(plain).isSuccess()) {
                    return false;
                }
                Resource saved = daos.resourceDao().findById(plain.getId()).orElseThrow();
                return saved.getDescription() == null && saved.getAcquisitionDate() == null
                        && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-002' "
                        + "AND descripcion IS NULL AND fecha_adquisicion IS NULL") == 1;
            });
            check("The code cannot be repeated (even in another case)", () -> {
                Result<Resource> r = resources.create(new Resource("zzp-001", "Otro nombre", ResourceCategory.BOOK, null, null, "Biblioteca"));
                return !r.isSuccess() && r.getMessage().contains("código ZZP-001")
                        && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-001'") == 1;
            });
            check("The same name with a different code is accepted (several copies of the same item)", () ->
                    resources.create(new Resource("ZZP-003", "Microscopio de prueba", ResourceCategory.LAB_EQUIPMENT, null, null, "Laboratorio")).isSuccess());
            check("A new resource must be Disponible", () -> {
                Resource borrowed = withStatus(new Resource("ZZP-004", "Recurso prestado", ResourceCategory.BOOK, null, null, "Biblioteca"),
                        ResourceCategory.BOOK, ResourceStatus.BORROWED);
                Result<Resource> r = resources.create(borrowed);
                return !r.isSuccess() && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-004'") == 0;
            });
            check("The database is the last defence: a repeated code, or a missing location, is refused and translated", () -> {
                boolean duplicate;
                try {
                    daos.resourceDao().create(new Resource("ZZP-001", "Duplicado", ResourceCategory.BOOK, null, null, "Biblioteca"));
                    duplicate = false;
                } catch (DaoException e) {
                    duplicate = e.getMessage().contains("Ya existe");
                }
                return duplicate && translatedFailure("INSERT INTO recursos (codigo_recurso, nombre_recurso, id_categoria, estado_recurso) "
                        + "VALUES ('ZZP-NOLOC', 'Sin ubicacion', 1, 'Disponible')")
                        && translatedFailure("INSERT INTO recursos (codigo_recurso, nombre_recurso, id_categoria, ubicacion_fisica, estado_recurso) "
                        + "VALUES ('ZZP-BADCAT', 'Categoria falsa', 99, 'Bodega', 'Disponible')");
            });

            // ---- editing ----
            check("Edit a resource: the new data is saved and the id is kept", () -> {
                Resource changed = new Resource("ZZP-001", "Microscopio de prueba mejorado", ResourceCategory.TECH_DEVICE,
                        null, null, "Sala de Informática - Armario 1");
                changed.setId(microscope.getId());
                if (!resources.update(changed).isSuccess()) {
                    return false;
                }
                Resource saved = daos.resourceDao().findById(microscope.getId()).orElseThrow();
                return saved.getName().equals("Microscopio de prueba mejorado") && saved.getCategory() == ResourceCategory.TECH_DEVICE
                        && saved.getDescription() == null && saved.getAcquisitionDate() == null
                        && saved.getLocation().equals("Sala de Informática - Armario 1");
            });
            check("An edit never changes the state (the state only changes through its own operation)", () -> {
                Resource sneaky = new Resource("ZZP-001", "Microscopio de prueba mejorado", ResourceCategory.TECH_DEVICE,
                        null, null, "Sala de Informática - Armario 1");
                sneaky.setId(microscope.getId());
                sneaky.setStatus(ResourceStatus.WRITTEN_OFF);
                return resources.update(sneaky).isSuccess()
                        && daos.resourceDao().findById(microscope.getId()).orElseThrow().getStatus() == ResourceStatus.AVAILABLE;
            });
            check("An edit cannot take the code of another resource, but can keep its own", () -> {
                Resource clash = new Resource("ZZP-002", "Microscopio", ResourceCategory.BOOK, null, null, "Biblioteca");
                clash.setId(microscope.getId());
                Resource keep = new Resource("ZZP-001", "Microscopio de prueba mejorado", ResourceCategory.TECH_DEVICE, null, null, "Sala");
                keep.setId(microscope.getId());
                return !resources.update(clash).isSuccess() && resources.update(keep).isSuccess()
                        && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-002'") == 1;
            });
            check("The code check ignores the resource being edited", () ->
                    resources.codeTaken("zzp-001", null).getData()
                            && !resources.codeTaken("ZZP-001", microscope.getId()).getData()
                            && !resources.codeTaken("ZZP-777", null).getData());

            // ---- changing the state ----
            Resource ball = new Resource("ZZP-010", "Balón de prueba", ResourceCategory.SPORTS_EQUIPMENT, null, null, "Coliseo");
            resources.create(ball);
            check("Disponible -> En Mantenimiento -> Disponible are saved in the database", () ->
                    resources.changeStatus(ball, ResourceStatus.MAINTENANCE).isSuccess() && ball.getStatus() == ResourceStatus.MAINTENANCE
                            && statusInDb("ZZP-010").equals("En Mantenimiento")
                            && resources.changeStatus(ball, ResourceStatus.AVAILABLE).isSuccess() && statusInDb("ZZP-010").equals("Disponible"));
            check("The same state, or Prestado by hand, is refused and nothing changes", () -> {
                Result<Resource> same = resources.changeStatus(ball, ResourceStatus.AVAILABLE);
                Result<Resource> borrow = resources.changeStatus(ball, ResourceStatus.BORROWED);
                return !same.isSuccess() && same.getMessage().contains("ya está")
                        && !borrow.isSuccess() && borrow.getMessage().contains("préstamo")
                        && ball.getStatus() == ResourceStatus.AVAILABLE && statusInDb("ZZP-010").equals("Disponible");
            });
            check("The allowed changes list matches the rule and explains why there are none", () -> {
                Result<List<ResourceStatus>> fromAvailable = resources.allowedChanges(ball);
                Resource borrowed = withStatus(resource("ZZP-011", "Prestado"), ResourceCategory.BOOK, ResourceStatus.BORROWED);
                Resource off = withStatus(resource("ZZP-012", "De baja"), ResourceCategory.BOOK, ResourceStatus.WRITTEN_OFF);
                Result<List<ResourceStatus>> fromBorrowed = resources.allowedChanges(borrowed);
                Result<List<ResourceStatus>> fromOff = resources.allowedChanges(off);
                return fromAvailable.isSuccess() && fromAvailable.getData().equals(List.of(ResourceStatus.MAINTENANCE, ResourceStatus.WRITTEN_OFF))
                        && !fromBorrowed.isSuccess() && fromBorrowed.getMessage().contains("devolverse")
                        && !fromOff.isSuccess() && fromOff.getMessage().contains("dado de baja");
            });
            check("A borrowed resource cannot be changed by hand; it must be returned first", () -> {
                Resource lent = new Resource("ZZP-013", "Recurso en préstamo", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(lent);
                execute("UPDATE recursos SET estado_recurso = 'Prestado' WHERE codigo_recurso = 'ZZP-013'");
                lent.setStatus(ResourceStatus.BORROWED);
                Result<Resource> toMaintenance = resources.changeStatus(lent, ResourceStatus.MAINTENANCE);
                Result<Resource> toOff = resources.changeStatus(lent, ResourceStatus.WRITTEN_OFF);
                return !toMaintenance.isSuccess() && toMaintenance.getMessage().contains("devolverse")
                        && !toOff.isSuccess() && statusInDb("ZZP-013").equals("Prestado");
            });
            check("Dado de Baja is final: it cannot go back to Disponible", () -> {
                Resource old = new Resource("ZZP-014", "Recurso viejo", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(old);
                if (!resources.changeStatus(old, ResourceStatus.WRITTEN_OFF).isSuccess()) {
                    return false;
                }
                Result<Resource> back = resources.changeStatus(old, ResourceStatus.AVAILABLE);
                return !back.isSuccess() && back.getMessage().contains("dado de baja")
                        && statusInDb("ZZP-014").equals("Dado de Baja") && old.getStatus() == ResourceStatus.WRITTEN_OFF;
            });
            check("If the state changed in the database after the resource was read, the change is refused and the object is not touched", () -> {
                Resource stale = new Resource("ZZP-015", "Recurso desactualizado", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(stale);
                execute("UPDATE recursos SET estado_recurso = 'En Mantenimiento' WHERE codigo_recurso = 'ZZP-015'");
                Result<Resource> r = resources.changeStatus(stale, ResourceStatus.WRITTEN_OFF);
                return !r.isSuccess() && r.getMessage().contains("cambió") && stale.getStatus() == ResourceStatus.AVAILABLE
                        && statusInDb("ZZP-015").equals("En Mantenimiento");
            });

            // ---- deleting ----
            check("A resource that was never lent can be deleted", () -> {
                Resource never = new Resource("ZZP-020", "Nunca prestado", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(never);
                Result<Resource> r = resources.delete(never);
                return r.isSuccess() && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-020'") == 0;
            });
            check("Deleting a resource that no longer exists says so", () -> {
                Resource gone = new Resource("ZZP-021", "Ya borrado", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(gone);
                execute("DELETE FROM recursos WHERE codigo_recurso = 'ZZP-021'");
                Result<Resource> r = resources.delete(gone);
                return !r.isSuccess() && r.getMessage().contains("ya no existe");
            });
            check("A resource with a student loan in its history is NOT deleted and its loan stays", () -> {
                Resource lent = new Resource("ZZP-022", "Prestado a estudiante", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(lent);
                execute("INSERT INTO prestamos_estudiante (id_recurso, id_estudiante, fecha_prestamo, fecha_devolucion_esperada, "
                        + "fecha_devolucion_real, estado_prestamo) SELECT id_recurso, 1, '2026-02-02', '2026-02-09', '2026-02-08', 'Devuelto' "
                        + "FROM recursos WHERE codigo_recurso = 'ZZP-022'");
                Result<Resource> r = resources.delete(lent);
                return !r.isSuccess() && r.getMessage().contains("Dado de Baja")
                        && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-022'") == 1
                        && scalar("SELECT COUNT(*) FROM prestamos_estudiante pe JOIN recursos r ON r.id_recurso = pe.id_recurso "
                        + "WHERE r.codigo_recurso = 'ZZP-022'") == 1;
            });
            check("A resource with a teacher loan in its history is NOT deleted either", () -> {
                Resource lent = new Resource("ZZP-023", "Prestado a profesor", ResourceCategory.BOOK, null, null, "Biblioteca");
                resources.create(lent);
                execute("INSERT INTO prestamos_profesor (id_recurso, id_profesor, fecha_prestamo, fecha_devolucion_esperada, "
                        + "fecha_devolucion_real, estado_prestamo) SELECT id_recurso, 1, '2026-02-02', '2026-02-09', '2026-02-08', 'Devuelto' "
                        + "FROM recursos WHERE codigo_recurso = 'ZZP-023'");
                Result<Resource> r = resources.delete(lent);
                return !r.isSuccess() && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-023'") == 1;
            });
            check("A resource marked Prestado is not deleted even if it has no loan row yet", () ->
                    !resources.delete(withId(resource("ZZP-013", "Recurso en préstamo"), idOf("ZZP-013"))).isSuccess()
                            && scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso = 'ZZP-013'") == 1);

            // ---- searching and the inventory ----
            check("Search finds by code (any case), by name, by location and by category, ignoring accents", () ->
                    codes(resources.search("zzp-001")).equals(List.of("ZZP-001"))
                            && codes(resources.search("balón de prueba")).equals(List.of("ZZP-010"))
                            && codes(resources.search("balon de prueba")).equals(List.of("ZZP-010"))
                            && codes(resources.search("coliseo")).contains("ZZP-010")
                            && codes(resources.search("sala de informatica")).contains("TEC-001")
                            && resources.search("libro").getData().size() >= 14
                            && resources.search("zzzz-no-existe").getData().isEmpty());
            check("Search treats % and _ as normal characters and refuses an empty text", () ->
                    resources.search("%").getData().isEmpty() && resources.search("_").getData().isEmpty()
                            && !resources.search("   ").isSuccess());
            check("The inventory is sorted by code and its totals add up", () -> {
                ResourceInventory all = resources.inventory(null, null).getData();
                List<String> codes = all.resources().stream().map(Resource::getCode).toList();
                return codes.equals(codes.stream().sorted().toList())
                        && all.byStatus().values().stream().mapToLong(Long::longValue).sum() == all.total()
                        && all.byCategory().values().stream().mapToLong(Long::longValue).sum() == all.total()
                        && all.total() == scalar("SELECT COUNT(*) FROM recursos");
            });
            check("The inventory can be filtered by category and by state", () -> {
                ResourceInventory books = resources.inventory(ResourceCategory.BOOK, null).getData();
                ResourceInventory maintenance = resources.inventory(null, ResourceStatus.MAINTENANCE).getData();
                ResourceInventory both = resources.inventory(ResourceCategory.LAB_EQUIPMENT, ResourceStatus.MAINTENANCE).getData();
                return books.resources().stream().allMatch(r -> r.getCategory() == ResourceCategory.BOOK)
                        && books.total() == scalar("SELECT COUNT(*) FROM recursos WHERE id_categoria = 1")
                        && maintenance.resources().stream().allMatch(r -> r.getStatus() == ResourceStatus.MAINTENANCE)
                        && maintenance.total() == scalar("SELECT COUNT(*) FROM recursos WHERE estado_recurso = 'En Mantenimiento'")
                        && both.resources().stream().allMatch(r -> r.getCategory() == ResourceCategory.LAB_EQUIPMENT
                        && r.getStatus() == ResourceStatus.MAINTENANCE);
            });

            // ---- the sample data of docs/database.sql ----
            check("Sample: at least 30 resources, and every one passes the rules of the entity when it is read", () ->
                    scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso NOT LIKE 'ZZP-%'") >= 30
                            && resources.inventory(null, null).isSuccess());
            check("Sample: the five categories and the four states are all represented", () ->
                    scalar("SELECT COUNT(DISTINCT id_categoria) FROM recursos WHERE codigo_recurso NOT LIKE 'ZZP-%'") == 5
                            && scalar("SELECT COUNT(DISTINCT estado_recurso) FROM recursos WHERE codigo_recurso NOT LIKE 'ZZP-%'") == 4);
            check("Sample: every resource has a location and every code is unique", () ->
                    scalar("SELECT COUNT(*) FROM recursos WHERE ubicacion_fisica IS NULL OR TRIM(ubicacion_fisica) = ''") == 0
                            && scalar("SELECT COUNT(*) - COUNT(DISTINCT codigo_recurso) FROM recursos") == 0);
            check("Sample: three donated books have no acquisition date, and the three copies of the microscope name keep different codes", () ->
                    scalar("SELECT COUNT(*) FROM recursos WHERE codigo_recurso IN ('LIB-002','LIB-003','LIB-005') AND fecha_adquisicion IS NULL") == 3
                            && codes(resources.search("Tablet Samsung")).size() == 5);
            check("Sample: the Prestado resources cannot be touched by hand and written-off ones cannot come back", () -> {
                Resource borrowed = daos.resourceDao().findAll().stream().filter(r -> r.getCode().equals("LIB-001")).findFirst().orElseThrow();
                Resource off = daos.resourceDao().findAll().stream().filter(r -> r.getCode().equals("LIB-004")).findFirst().orElseThrow();
                return borrowed.getStatus() == ResourceStatus.BORROWED && !resources.allowedChanges(borrowed).isSuccess()
                        && off.getStatus() == ResourceStatus.WRITTEN_OFF && !resources.allowedChanges(off).isSuccess();
            });
        } finally {
            cleanUp();
        }

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----

    private static Resource resource(String code, String name) {
        return new Resource(code, name, ResourceCategory.BOOK, null, null, "Biblioteca - Estante Z");
    }

    private static Resource withStatus(Resource resource, ResourceCategory category, ResourceStatus status) {
        resource.setCategory(category);
        resource.setStatus(status);
        return resource;
    }

    private static Resource withId(Resource resource, long id) {
        resource.setId((int) id);
        return resource;
    }

    private static long idOf(String code) throws SQLException, DaoException {
        return scalar("SELECT id_recurso FROM recursos WHERE codigo_recurso = '" + code + "'");
    }

    private static List<String> codes(Result<List<Resource>> result) {
        return result.getData().stream().map(Resource::getCode).toList();
    }

    private static String statusInDb(String code) throws SQLException, DaoException {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT estado_recurso FROM recursos WHERE codigo_recurso = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : "(no existe)";
            }
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

    /** Deletes what this check created; the loans it inserted go with their resource (ON DELETE CASCADE). */
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
