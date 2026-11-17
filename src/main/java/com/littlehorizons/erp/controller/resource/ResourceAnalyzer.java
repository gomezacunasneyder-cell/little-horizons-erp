package com.littlehorizons.erp.controller.resource;

import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.util.EnumCounts;

import java.util.List;
import java.util.function.Predicate;

/** Pure calculations over a list of resources (no database): the filter and the totals, done with lambdas and streams. */
public final class ResourceAnalyzer {

    private ResourceAnalyzer() {
    }

    /** Keeps the resources that match every filter that was given (null = do not filter by that). */
    public static List<Resource> filter(List<Resource> resources, ResourceCategory category, ResourceStatus status) {
        Predicate<Resource> byCategory = resource -> category == null || resource.getCategory() == category;
        Predicate<Resource> byStatus = resource -> status == null || resource.getStatus() == status;
        return resources.stream().filter(byCategory.and(byStatus)).toList();
    }

    public static ResourceInventory inventory(List<Resource> resources) {
        return new ResourceInventory(resources,
                EnumCounts.countBy(ResourceStatus.class, resources, Resource::getStatus),
                EnumCounts.countBy(ResourceCategory.class, resources, Resource::getCategory));
    }
}
