package com.littlehorizons.erp.controller.resource;

import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
                countBy(ResourceStatus.class, resources, Resource::getStatus),
                countBy(ResourceCategory.class, resources, Resource::getCategory));
    }

    /** Counts the resources per enum constant, keeping the constants that have none (with 0) in declaration order. */
    private static <E extends Enum<E>> Map<E, Long> countBy(Class<E> type, List<Resource> resources,
                                                              Function<Resource, E> key) {
        Map<E, Long> counted = resources.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        Map<E, Long> all = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            all.put(constant, counted.getOrDefault(constant, 0L));
        }
        return all;
    }
}
