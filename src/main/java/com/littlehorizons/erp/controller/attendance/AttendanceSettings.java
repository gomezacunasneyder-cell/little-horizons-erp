package com.littlehorizons.erp.controller.attendance;

import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.util.AppConfig;

/**
 * Reads the optional attendance keys of config.txt:
 * attendance.weekly.absence.limit = number of absences in a week that is still acceptable
 * (default 3, so a student with 4 or more is reported).
 */
public final class AttendanceSettings {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 30;

    private AttendanceSettings() {
    }

    public static int weeklyAbsenceLimit(AppConfig config) {
        return parseLimit(config.optional("attendance.weekly.absence.limit", String.valueOf(DEFAULT_LIMIT)));
    }

    /** Converts the text of config.txt into the limit, or fails with a message that says what to fix. */
    public static int parseLimit(String value) {
        String text = value.trim();
        try {
            int limit = Integer.parseInt(text);
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new NumberFormatException();
            }
            return limit;
        } catch (NumberFormatException e) {
            throw new ConfigurationException("attendance.weekly.absence.limit debe ser un número entre 1 y "
                    + MAX_LIMIT + " (ejemplo: 3), no «" + text + "».");
        }
    }
}
