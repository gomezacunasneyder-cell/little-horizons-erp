package com.littlehorizons.erp.controller.resource;

import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.util.List;
import java.util.Map;

/** A list of resources with the totals shown under it. Both maps contain EVERY state / category, with 0 when none. */
public record ResourceInventory(List<Resource> resources, Map<ResourceStatus, Long> byStatus,
                                Map<ResourceCategory, Long> byCategory) {

    public int total() {
        return resources.size();
    }
}
