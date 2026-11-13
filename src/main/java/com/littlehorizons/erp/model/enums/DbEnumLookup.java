package com.littlehorizons.erp.model.enums;

/**
 * Finds the enum constant that matches a database label. One generic method serves every enum
 * (the bound {@code E extends Enum<E> & DbEnum} means "any enum that implements DbEnum"), so the
 * DAOs do not repeat a loop per enum.
 */
public final class DbEnumLookup {

    private DbEnumLookup() {
    }

    public static <E extends Enum<E> & DbEnum> E fromDbLabel(Class<E> type, String label) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getDbLabel().equals(label)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " label: " + label);
    }
}
