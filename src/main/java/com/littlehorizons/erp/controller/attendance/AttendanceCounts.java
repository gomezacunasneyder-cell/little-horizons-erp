package com.littlehorizons.erp.controller.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How many times each state was registered. The attendance rate is the share of the classes in
 * which the student was present or late, without counting the classes excused by a medical
 * reason (an excused absence does not hurt the student).
 */
public record AttendanceCounts(int present, int absent, int late, int excused) {

    public static final AttendanceCounts EMPTY = new AttendanceCounts(0, 0, 0, 0);

    public int total() {
        return present + absent + late + excused;
    }

    public AttendanceCounts plus(AttendanceCounts other) {
        return new AttendanceCounts(present + other.present, absent + other.absent,
                late + other.late, excused + other.excused);
    }

    /** Percentage with one decimal, or null when there are no classes to count. */
    public BigDecimal ratePercent() {
        int counted = total() - excused;
        if (counted == 0) {
            return null;
        }
        return BigDecimal.valueOf((present + late) * 100L)
                .divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP);
    }
}
