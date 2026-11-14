package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.dao.GradeLevelDao;
import com.littlehorizons.erp.model.dao.ScheduleDao;
import com.littlehorizons.erp.model.dao.SubjectDao;
import com.littlehorizons.erp.model.dao.TeachingAssignmentDao;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.ScheduleSlot;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rules for assigning subjects to teachers. A subject can have several teachers (one per
 * section), but a given subject + year + section has only one teacher, and only active teachers
 * can receive new assignments.
 */
public class AssignmentController extends BaseController {

    private static final double MINUTES_PER_HOUR = 60.0;

    private final TeachingAssignmentDao assignmentDao;
    private final ScheduleDao scheduleDao;
    private final SubjectDao subjectDao;
    private final GradeLevelDao gradeLevelDao;

    public AssignmentController(TeachingAssignmentDao assignmentDao, ScheduleDao scheduleDao,
                                SubjectDao subjectDao, GradeLevelDao gradeLevelDao, ErrorLogger logger) {
        super(logger);
        this.assignmentDao = assignmentDao;
        this.scheduleDao = scheduleDao;
        this.subjectDao = subjectDao;
        this.gradeLevelDao = gradeLevelDao;
    }

    public Result<List<GradeLevel>> grades() {
        return execute("List grades", () -> Result.ok(gradeLevelDao.findAll(), ""));
    }

    public Result<List<Subject>> subjectsOf(GradeLevel grade) {
        return execute("List subjects of grade", () -> Result.ok(subjectDao.findActiveByGrade(grade.getId()), ""));
    }

    public Result<TeachingAssignment> assign(Teacher teacher, Subject subject, int academicYear, String section) {
        return execute("Assign subject", () -> {
            if (teacher.getStatus() != TeacherStatus.ACTIVE) {
                return Result.fail("Solo se pueden asignar materias a profesores con estado Activo.");
            }
            TeachingAssignment assignment = new TeachingAssignment(teacher, subject, academicYear, section);
            var existing = assignmentDao.findBySubjectYearAndSection(subject.getId(), academicYear, assignment.getSection());
            if (existing.isPresent()) {
                return Result.fail(subject.getName() + " de " + assignment.getGroupName() + " ya la dicta "
                        + existing.get().getTeacher().getFullName() + " en " + academicYear + ".");
            }
            assignmentDao.create(assignment);
            return Result.ok(assignment, "Asignación registrada: " + assignment + ".");
        });
    }

    public Result<List<TeachingAssignment>> byTeacher(Teacher teacher, int academicYear) {
        return execute("List teacher assignments", () -> Result.ok(assignmentDao.findByTeacherAndYear(teacher.getId(), academicYear), ""));
    }

    public Result<List<TeachingAssignment>> bySubject(Subject subject, int academicYear) {
        return execute("List subject teachers", () -> Result.ok(assignmentDao.findBySubjectAndYear(subject.getId(), academicYear), ""));
    }

    public Result<Void> remove(TeachingAssignment assignment) {
        return execute("Remove assignment", () -> {
            int slots = scheduleDao.findByAssignment(assignment.getId()).size();
            assignmentDao.delete(assignment.getId());
            return Result.ok("Asignación eliminada" + (slots > 0 ? " junto con sus " + slots + " bloque(s) de horario." : "."));
        });
    }

    /**
     * Academic load: for every assignment, the hours per week already scheduled. The minutes of
     * each block are added up with a stream and grouped by assignment id.
     */
    public Result<TeacherWorkload> workload(Teacher teacher, int academicYear) {
        return execute("Calculate workload", () -> {
            List<TeachingAssignment> assignments = assignmentDao.findByTeacherAndYear(teacher.getId(), academicYear);
            Map<Integer, Long> minutesByAssignment = scheduleDao.findByTeacherAndYear(teacher.getId(), academicYear).stream()
                    .collect(Collectors.groupingBy(slot -> slot.getAssignment().getId(),
                            Collectors.summingLong(ScheduleSlot::getDurationMinutes)));

            List<TeacherWorkload.Line> lines = assignments.stream()
                    .map(a -> new TeacherWorkload.Line(a, minutesByAssignment.getOrDefault(a.getId(), 0L) / MINUTES_PER_HOUR))
                    .toList();
            double total = lines.stream().mapToDouble(TeacherWorkload.Line::weeklyHours).sum();
            return Result.ok(new TeacherWorkload(lines, total), "");
        });
    }
}
