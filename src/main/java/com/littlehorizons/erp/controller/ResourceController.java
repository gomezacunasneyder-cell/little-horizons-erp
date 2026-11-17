package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.resource.ResourceAnalyzer;
import com.littlehorizons.erp.controller.resource.ResourceInventory;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.ResourceDao;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;

/**
 * Rules of the resource catalog: the inventory code is unique; a new resource is always
 * "Disponible"; the state only changes by the paths ResourceStatus.manualTargets() allows
 * ("Prestado" belongs to the loans and "Dado de Baja" is final); and a resource that was ever
 * lent is never deleted (its loan history would go with it), it is written off instead.
 */
public class ResourceController extends BaseController {

    private final ResourceDao resourceDao;

    public ResourceController(ResourceDao resourceDao, ErrorLogger logger) {
        super(logger);
        this.resourceDao = resourceDao;
    }

    public Result<Resource> create(Resource resource) {
        return execute("Create resource", () -> {
            if (resource.getStatus() != ResourceStatus.AVAILABLE) {
                throw new ValidationException("Un recurso nuevo se registra como Disponible.");
            }
            requireFreeCode(resource);
            resourceDao.create(resource);
            return Result.ok(resource, "Recurso " + resource.getCode() + " «" + resource.getName() + "» registrado como Disponible.");
        });
    }

    /** Saves the edited data (the object carries its id). The state is not part of an edit. */
    public Result<Resource> update(Resource resource) {
        return execute("Update resource", () -> {
            requireFreeCode(resource);
            resourceDao.update(resource);
            return Result.ok(resource, "Recurso " + resource.getCode() + " actualizado.");
        });
    }

    /** The states the resource can be changed to by hand; fails with the reason when there are none. */
    public Result<List<ResourceStatus>> allowedChanges(Resource resource) {
        return execute("List allowed resource status changes", () -> {
            List<ResourceStatus> targets = List.copyOf(resource.getStatus().manualTargets());
            if (targets.isEmpty()) {
                throw new ValidationException(noChangeMessage(resource));
            }
            return Result.ok(targets, "");
        });
    }

    public Result<Resource> changeStatus(Resource resource, ResourceStatus target) {
        return execute("Change resource status", () -> {
            ResourceStatus current = resource.getStatus();
            if (current == target) {
                throw new ValidationException("El recurso ya está en estado «" + target.getDisplayName() + "».");
            }
            if (!current.manualTargets().contains(target)) {
                throw new ValidationException(current.manualTargets().isEmpty() ? noChangeMessage(resource)
                        : "No se puede pasar de «" + current.getDisplayName() + "» a «" + target.getDisplayName() + "» a mano."
                        + (target == ResourceStatus.BORROWED ? " El estado Prestado se asigna al registrar un préstamo." : ""));
            }
            if (!resourceDao.changeStatus(resource.getId(), current, target)) {
                throw new ValidationException("El estado del recurso cambió mientras tanto (por ejemplo, se prestó). "
                        + "Consúltelo de nuevo antes de cambiarlo.");
            }
            resource.setStatus(target);
            return Result.ok(resource, "El recurso " + resource.getCode() + " pasó de «" + current.getDisplayName()
                    + "» a «" + target.getDisplayName() + "».");
        });
    }

    /** Deletes a resource that never had a loan. One that was lent (or is borrowed now) is written off instead. */
    public Result<Resource> delete(Resource resource) {
        return execute("Delete resource", () -> {
            if (!resourceDao.deleteIfNeverBorrowed(resource.getId())) {
                if (resourceDao.findById(resource.getId()).isEmpty()) {
                    throw new ValidationException("El recurso " + resource.getCode() + " ya no existe.");
                }
                throw new ValidationException("No se puede eliminar " + resource.getCode() + ": está prestado o tiene "
                        + "préstamos registrados. Si ya no está en uso, márquelo como «Dado de Baja».");
            }
            return Result.ok(resource, "Recurso " + resource.getCode() + " «" + resource.getName() + "» eliminado del inventario.");
        });
    }

    /** The inventory sorted by code, optionally filtered by category and/or state (null = no filter), with its totals. */
    public Result<ResourceInventory> inventory(ResourceCategory category, ResourceStatus status) {
        return execute("Show resource inventory", () -> Result.ok(
                ResourceAnalyzer.inventory(ResourceAnalyzer.filter(resourceDao.findAll(), category, status)), ""));
    }

    public Result<List<Resource>> search(String term) {
        return execute("Search resources", () -> {
            if (term == null || term.isBlank()) {
                throw new ValidationException("Escriba parte del código, del nombre, de la ubicación o de la categoría.");
            }
            return Result.ok(resourceDao.search(term.trim()), "");
        });
    }

    public Result<Boolean> codeTaken(String code, Integer excludeId) {
        return execute("Check resource code", () -> Result.ok(resourceDao.codeExists(code.trim().toUpperCase(), excludeId), ""));
    }

    private void requireFreeCode(Resource resource) throws DaoException {
        if (resourceDao.codeExists(resource.getCode(), resource.getId())) {
            throw new ValidationException("Ya existe un recurso con el código " + resource.getCode() + ".");
        }
    }

    private static String noChangeMessage(Resource resource) {
        return resource.getStatus() == ResourceStatus.BORROWED
                ? "El recurso " + resource.getCode() + " está prestado: debe devolverse antes de cambiar su estado."
                : "El recurso " + resource.getCode() + " está dado de baja y su estado ya no se puede cambiar.";
    }
}
