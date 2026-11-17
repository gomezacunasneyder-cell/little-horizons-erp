package com.littlehorizons.erp.util;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The totals "how many per state / category / type" that several reports show, written once with streams. */
public final class EnumCounts {

    private EnumCounts() {
    }

    /**
     * Counts the items per enum constant. Every constant of the enum is in the result, in
     * declaration order, with 0 when no item has it, so a report always shows the same rows.
     */
    public static <T, E extends Enum<E>> Map<E, Long> countBy(Class<E> type, Collection<T> items, Function<T, E> key) {
        Map<E, Long> counted = items.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        Map<E, Long> all = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            all.put(constant, counted.getOrDefault(constant, 0L));
        }
        return all;
    }
}
