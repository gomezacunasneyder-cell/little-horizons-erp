package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.ScheduleDao;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.ScheduleSlot;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.Weekday;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Class schedules and the basic conflict rules: a teacher cannot be in two classes at the same
 * time, and a group (grade + section) cannot have two classes at the same time either. The
 * overlap test is a Predicate (lambda) applied to the blocks already saved.
 */
public class ScheduleController extends BaseController {

    private final ScheduleDao scheduleDao;

    public ScheduleController(ScheduleDao scheduleDao, ErrorLogger logger) {
        super(logger);
        this.scheduleDao = scheduleDao;
    }

    public Result<ScheduleSlot> addSlot(TeachingAssignment assignment, Weekday day, LocalTime start, LocalTime end) {
        return execute("Add schedule slot", () -> {
            ScheduleSlot slot = new ScheduleSlot(assignment, day, start, end); // validates the times
            Predicate<ScheduleSlot> overlapsNewSlot = existing -> existing.overlaps(day, start, end);

            Optional<ScheduleSlot> teacherConflict = scheduleDao
                    .findByTeacherAndYear(assignment.getTeacher().getId(), assignment.getAcademicYear())
                    .stream().filter(overlapsNewSlot).findFirst();
            if (teacherConflict.isPresent()) {
                ScheduleSlot other = teacherConflict.get();
                return Result.fail("Choque de horario: el profesor ya dicta " + other.getAssignment().getSubject().getName()
                        + " a " + other.getAssignment().getGroupName() + " el " + other.getTimeText() + ".");
            }

            Optional<ScheduleSlot> groupConflict = scheduleDao
                    .findByGroup(assignment.getSubject().getGradeLevel().getId(), assignment.getSection(), assignment.getAcademicYear())
                    .stream().filter(overlapsNewSlot).findFirst();
            if (groupConflict.isPresent()) {
                ScheduleSlot other = groupConflict.get();
                return Result.fail("Choque de horario: " + assignment.getGroupName() + " ya tiene "
                        + other.getAssignment().getSubject().getName() + " el " + other.getTimeText() + ".");
            }

            scheduleDao.create(slot);
            return Result.ok(slot, "Horario registrado: " + slot.getTimeText() + ".");
        });
    }

    public Result<Void> removeSlot(ScheduleSlot slot) {
        return execute("Remove schedule slot", () -> {
            scheduleDao.delete(slot.getId());
            return Result.ok("Bloque de horario eliminado.");
        });
    }

    public Result<List<ScheduleSlot>> slotsOf(TeachingAssignment assignment) {
        return execute("List assignment slots", () -> Result.ok(scheduleDao.findByAssignment(assignment.getId()), ""));
    }

    /** The teacher's week: a map day -> classes of that day sorted by start time (days in week order). */
    public Result<Map<Weekday, List<ScheduleSlot>>> weeklyScheduleOf(Teacher teacher, int academicYear) {
        return execute("Load teacher schedule", () -> Result.ok(groupByDay(
                scheduleDao.findByTeacherAndYear(teacher.getId(), academicYear)), ""));
    }

    public Result<Map<Weekday, List<ScheduleSlot>>> weeklyScheduleOfGroup(GradeLevel grade, String section, int academicYear) {
        return execute("Load group schedule", () -> Result.ok(groupByDay(
                scheduleDao.findByGroup(grade.getId(), section.trim().toUpperCase(), academicYear)), ""));
    }

    private Map<Weekday, List<ScheduleSlot>> groupByDay(List<ScheduleSlot> slots) {
        return slots.stream()
                .sorted(Comparator.comparing(ScheduleSlot::getStart))
                .collect(Collectors.groupingBy(ScheduleSlot::getDay, TreeMap::new, Collectors.toList()));
    }
}
