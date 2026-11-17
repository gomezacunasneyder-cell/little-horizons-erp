package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.model.entity.Resource;

import java.util.List;
import java.util.function.Function;

/** Dialog to find and pick one resource; the generic work is done by EntitySelector. */
final class ResourceSelector {

    private final EntitySelector<Resource> selector;

    ResourceSelector(ConsoleInput input, ConsoleOutput output, Function<String, Result<List<Resource>>> search) {
        this.selector = new EntitySelector<>(input, output, "Código, nombre o ubicación del recurso: ", search,
                r -> r.getCode() + " - " + r.getName() + " [" + r.getStatus().getDisplayName() + "]");
    }

    Resource select() {
        return selector.select();
    }
}
