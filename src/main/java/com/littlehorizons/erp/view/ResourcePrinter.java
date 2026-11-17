package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.resource.ResourceInventory;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.DbEnum;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Prints resources as tables and lines. It only formats; the totals come already calculated. */
final class ResourcePrinter {

    private final ConsoleOutput output;

    ResourcePrinter(ConsoleOutput output) {
        this.output = output;
    }

    void inventory(ResourceInventory inventory) {
        table(inventory.resources());
        if (inventory.total() > 0) {
            output.info("Total: " + inventory.total() + " recurso(s).");
            output.info("Por estado:    " + totals(inventory.byStatus()));
            output.info("Por categoría: " + totals(inventory.byCategory()));
        }
    }

    void table(List<Resource> resources) {
        output.table(List.of("Código", "Nombre", "Categoría", "Estado", "Ubicación", "Adquirido"),
                resources.stream().map(this::row).toList());
    }

    private List<String> row(Resource r) {
        return List.of(r.getCode(), r.getName(), r.getCategory().getDisplayName(), r.getStatus().getDisplayName(),
                r.getLocation(), r.getAcquisitionDate() == null ? "-" : r.getAcquisitionDate().toString());
    }

    private static <E extends Enum<E> & DbEnum> String totals(Map<E, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName() + " " + entry.getValue())
                .collect(Collectors.joining("  |  "));
    }

    /** The data of one resource, shown before editing, changing or deleting it. */
    void detail(Resource resource) {
        output.line("  Código:       " + resource.getCode());
        output.line("  Nombre:       " + resource.getName());
        output.line("  Categoría:    " + resource.getCategory().getDisplayName());
        output.line("  Estado:       " + resource.getStatus().getDisplayName());
        output.line("  Ubicación:    " + resource.getLocation());
        output.line("  Adquisición:  " + (resource.getAcquisitionDate() == null ? "-" : resource.getAcquisitionDate()));
        output.line("  Descripción:  " + (resource.getDescription() == null ? "-" : resource.getDescription()));
    }
}
