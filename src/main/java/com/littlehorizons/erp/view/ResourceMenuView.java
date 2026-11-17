package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.resource.ResourceInventory;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * "Gestión de Recursos - Catálogo": the inventory of lendable items (books, lab equipment, sports
 * equipment, devices). It only asks for data and prints; the rules (unique code, allowed state
 * changes, no deleting what was lent) are in the controller, the entity and the enums.
 */
public final class ResourceMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Ver el inventario completo",
            "Buscar recursos (código, nombre, ubicación o categoría)",
            "Filtrar por categoría y estado",
            "Registrar un recurso",
            "Editar un recurso",
            "Cambiar el estado de un recurso (mantenimiento, disponible o baja)",
            "Eliminar un recurso (solo si nunca se ha prestado)");

    private final ResourceController resources;
    private final ResourceSelector selector;
    private final ResourceForm form;
    private final ResourcePrinter resourcePrinter;
    private final ResultPrinter printer;

    public ResourceMenuView(ConsoleInput input, ConsoleOutput output, ResourceController resources) {
        super(input, output);
        this.resources = resources;
        this.selector = new ResourceSelector(input, output, resources::search);
        this.form = new ResourceForm(input, resources);
        this.resourcePrinter = new ResourcePrinter(output);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "GESTIÓN DE RECURSOS - CATÁLOGO";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> showInventory(null, null);
            case 2 -> search();
            case 3 -> filter();
            case 4 -> createResource();
            case 5 -> editResource();
            case 6 -> changeStatus();
            case 7 -> deleteResource();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void showInventory(ResourceCategory category, ResourceStatus status) {
        Result<ResourceInventory> result = resources.inventory(category, status);
        if (result.isSuccess()) {
            resourcePrinter.inventory(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void search() {
        Result<List<Resource>> result = resources.search(input.readRequiredText("Texto a buscar: ", 100));
        if (result.isSuccess()) {
            resourcePrinter.table(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void filter() {
        ResourceCategory category = readFilter("Categoría (número): ", ResourceCategory.values(),
                "Todas las categorías", ResourceCategory::getDisplayName);
        ResourceStatus status = readFilter("Estado (número): ", ResourceStatus.values(),
                "Todos los estados", ResourceStatus::getDisplayName);
        showInventory(category, status);
    }

    /** Shows "all" plus every value as a numbered list; choosing "all" returns null (= no filter). */
    private <T> T readFilter(String prompt, T[] values, String allLabel, Function<T, String> label) {
        List<T> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(List.of(values));
        return input.readOption(prompt, choices, value -> value == null ? allLabel : label.apply(value));
    }

    private void createResource() {
        printer.report(resources.create(form.readNew()));
    }

    private void editResource() {
        Resource resource = selector.select();
        output.info("Datos actuales:");
        resourcePrinter.detail(resource);
        printer.report(resources.update(form.readChanges(resource)));
    }

    private void changeStatus() {
        Resource resource = selector.select();
        output.info("Datos actuales:");
        resourcePrinter.detail(resource);
        Result<List<ResourceStatus>> allowed = resources.allowedChanges(resource);
        if (!allowed.isSuccess()) {
            output.error(allowed.getMessage());
            return;
        }
        ResourceStatus target = input.readOption("Nuevo estado (número): ", allowed.getData(), ResourceStatus::getDisplayName);
        if (target == ResourceStatus.WRITTEN_OFF
                && !input.readYesNo("La baja es definitiva: el recurso no volverá a estar disponible. ¿Continuar?")) {
            output.info("No se cambió nada.");
            return;
        }
        printer.report(resources.changeStatus(resource, target));
    }

    private void deleteResource() {
        Resource resource = selector.select();
        output.info("Se eliminará este recurso:");
        resourcePrinter.detail(resource);
        if (input.readYesNo("¿Eliminarlo definitivamente? No se puede deshacer")) {
            printer.report(resources.delete(resource));
        } else {
            output.info("No se eliminó nada.");
        }
    }
}
