package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;

import java.time.LocalDate;
import java.util.List;

/** Asks the data of a new resource, or the changes of an existing one, until every answer is valid. */
final class ResourceForm {

    private static final String DATE_PROMPT = "aaaa-mm-dd (ejemplo: 2024-03-15)";

    private final ConsoleInput input;
    private final ResourceController controller;

    ResourceForm(ConsoleInput input, ResourceController controller) {
        this.input = input;
        this.controller = controller;
    }

    Resource readNew() {
        String code = input.read("Código de inventario (letras, números y guiones, ejemplo LIB-001): ",
                text -> checkFreeCode(text, null));
        String name = input.read("Nombre (ejemplo: Microscopio óptico binocular): ", Resource::checkName);
        ResourceCategory category = readCategory();
        String description = input.read("Descripción (Enter para omitir): ", Resource::checkDescription);
        LocalDate acquired = readAcquisitionDate("Fecha de adquisición " + DATE_PROMPT + ", Enter para omitir: ");
        String location = input.read("Ubicación física (ejemplo: Biblioteca - Estante A1): ", Resource::checkLocation);
        return new Resource(code, name, category, description, acquired, location);
    }

    /**
     * Returns a NEW Resource with the changes (same id and state). The original is not touched,
     * so if the user cancels in the middle or the controller rejects the change, nothing is half-edited.
     */
    Resource readChanges(Resource current) {
        Integer id = current.getId();
        String code = input.read("Código [" + current.getCode() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getCode() : checkFreeCode(text, id));
        String name = input.read("Nombre [" + current.getName() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getName() : Resource.checkName(text));
        ResourceCategory category = input.readYesNo("¿Cambiar la categoría? (hoy: " + current.getCategory().getDisplayName() + ")")
                ? readCategory() : current.getCategory();
        String description = input.readYesNo("¿Cambiar la descripción?")
                ? input.read("Nueva descripción (Enter para dejarla vacía): ", Resource::checkDescription)
                : current.getDescription();
        LocalDate acquired = input.readYesNo("¿Cambiar la fecha de adquisición? (hoy: "
                + (current.getAcquisitionDate() == null ? "sin fecha" : current.getAcquisitionDate()) + ")")
                ? readAcquisitionDate("Nueva fecha " + DATE_PROMPT + ", Enter para dejarla vacía: ")
                : current.getAcquisitionDate();
        String location = input.read("Ubicación [" + current.getLocation() + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getLocation() : Resource.checkLocation(text));
        Resource changed = new Resource(code, name, category, description, acquired, location);
        changed.setId(id);
        changed.setStatus(current.getStatus());
        return changed;
    }

    private ResourceCategory readCategory() {
        return input.readOption("Categoría (número): ", List.of(ResourceCategory.values()), ResourceCategory::getDisplayName);
    }

    private LocalDate readAcquisitionDate(String prompt) {
        return input.read(prompt, text -> text.isEmpty() ? null : Resource.checkAcquisitionDate(LocalDate.parse(text)));
    }

    private String checkFreeCode(String text, Integer excludeId) {
        String code = Resource.checkCode(text);
        Result<Boolean> taken = controller.codeTaken(code, excludeId);
        if (taken.isSuccess() && taken.getData()) {
            throw new ValidationException("Ya existe un recurso con el código " + code + ".");
        }
        return code;
    }
}
