package com.littlehorizons.erp.model.enums;

/**
 * Implemented by every enum that mirrors a MySQL ENUM column. MySQL stores labels such as
 * "Excusa Médica" or "Tarjeta Débito", which are not valid Java constant names, so each Java
 * constant carries the exact database label plus a name to show to the user.
 */
public interface DbEnum {

    /** Exact text stored in the database column. */
    String getDbLabel();

    /** Text shown to the user in menus and tables. */
    String getDisplayName();
}
