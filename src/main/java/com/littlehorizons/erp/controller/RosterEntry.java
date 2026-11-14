package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.Student;

/** A student of a group together with the grade already registered (null if none yet). */
public record RosterEntry(Student student, AcademicRecord existing) {
}
