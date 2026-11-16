package com.littlehorizons.erp.model.dao.jdbc;

import com.littlehorizons.erp.model.entity.*;
import com.littlehorizons.erp.model.enums.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Time;

/**
 * Converts one row of a ResultSet into an entity. It is the only place that knows the Spanish
 * column names, so the rest of the code stays in English and the mapping is not repeated in
 * every DAO (DRY).
 */
final class RowMapper {

    private RowMapper() {
    }

    static Student student(ResultSet rs) throws SQLException {
        return Student.builder()
                .id(rs.getInt("id_estudiante"))
                .documentType(DbEnumLookup.fromDbLabel(DocumentType.class, rs.getString("tipo_documento")))
                .documentNumber(rs.getString("numero_documento"))
                .firstName(rs.getString("primer_nombre"))
                .middleName(rs.getString("segundo_nombre"))
                .lastName(rs.getString("primer_apellido"))
                .secondLastName(rs.getString("segundo_apellido"))
                .gender(DbEnumLookup.fromDbLabel(Gender.class, rs.getString("genero")))
                .birthDate(date(rs, "fecha_nacimiento"))
                .address(rs.getString("direccion"))
                .phone(rs.getString("telefono"))
                .email(rs.getString("correo_electronico"))
                .bloodType(rs.getString("tipo_sangre"))
                .allergies(rs.getString("alergias"))
                .medicalConditions(rs.getString("condiciones_medicas"))
                .admissionDate(date(rs, "fecha_ingreso"))
                .status(DbEnumLookup.fromDbLabel(StudentStatus.class, rs.getString("estado")))
                .build();
    }

    static Guardian guardian(ResultSet rs) throws SQLException {
        Guardian guardian = new Guardian(
                DbEnumLookup.fromDbLabel(DocumentType.class, rs.getString("tipo_documento")),
                rs.getString("numero_documento"),
                rs.getString("primer_nombre"),
                rs.getString("primer_apellido"),
                rs.getString("parentesco"),
                rs.getString("telefono_principal"));
        guardian.setId(rs.getInt("id_acudiente"));
        guardian.setMiddleName(rs.getString("segundo_nombre"));
        guardian.setSecondLastName(rs.getString("segundo_apellido"));
        guardian.setSecondaryPhone(rs.getString("telefono_secundario"));
        guardian.setEmail(rs.getString("correo_electronico"));
        guardian.setAddress(rs.getString("direccion"));
        guardian.setOccupation(rs.getString("ocupacion"));
        guardian.setFinanciallyResponsible(rs.getBoolean("es_responsable_financiero"));
        return guardian;
    }

    static GradeLevel gradeLevel(ResultSet rs) throws SQLException {
        return new GradeLevel(rs.getInt("id_grado"), rs.getString("nombre_grado"), rs.getInt("nivel_numerico"));
    }

    static Subject subject(ResultSet rs) throws SQLException {
        return new Subject(rs.getInt("id_materia"), rs.getString("codigo_materia"),
                rs.getString("nombre_materia"), gradeLevel(rs), rs.getInt("horas_semanales"),
                rs.getBoolean("esta_activa"));
    }

    /** Needs the columns of estudiantes, grados and matriculas in the same row. */
    static Enrollment enrollment(ResultSet rs) throws SQLException {
        Enrollment enrollment = new Enrollment(student(rs), gradeLevel(rs),
                rs.getInt("anio_academico"), date(rs, "fecha_matricula"));
        enrollment.setId(rs.getInt("id_matricula"));
        enrollment.setStatus(DbEnumLookup.fromDbLabel(EnrollmentStatus.class, rs.getString("estado_matricula")));
        enrollment.setWithdrawalDate(date(rs, "fecha_retiro"));
        enrollment.setNotes(rs.getString("observaciones"));
        return enrollment;
    }

    /** Needs the columns of estudiantes, materias, grados and inscripciones_materia. */
    static SubjectRegistration registration(ResultSet rs) throws SQLException {
        SubjectRegistration registration = new SubjectRegistration(student(rs), subject(rs),
                rs.getInt("anio_academico"),
                DbEnumLookup.fromDbLabel(AcademicPeriod.class, rs.getString("periodo_academico")),
                date(rs, "fecha_inscripcion"));
        registration.setId(rs.getInt("id_inscripcion"));
        registration.setSection(rs.getString("seccion"));
        // inscripciones_materia.estado is aliased as registration_status in the queries
        registration.setStatus(DbEnumLookup.fromDbLabel(RegistrationStatus.class, rs.getString("registration_status")));
        return registration;
    }

    static Teacher teacher(ResultSet rs) throws SQLException {
        return teacher(rs, "");
    }

    /** Reads the teacher from columns whose names start with the prefix (see JdbcTeacherDao.teacherColumnsPrefixed). */
    static Teacher teacher(ResultSet rs, String prefix) throws SQLException {
        Teacher teacher = new Teacher(
                DbEnumLookup.fromDbLabel(DocumentType.class, rs.getString(prefix + "tipo_documento")),
                rs.getString(prefix + "numero_documento"),
                rs.getString(prefix + "primer_nombre"),
                rs.getString(prefix + "primer_apellido"),
                rs.getString(prefix + "correo_electronico"),
                rs.getString(prefix + "especializacion"),
                date(rs, prefix + "fecha_contratacion"));
        teacher.setId(rs.getInt(prefix + "id_profesor"));
        teacher.setMiddleName(rs.getString(prefix + "segundo_nombre"));
        teacher.setSecondLastName(rs.getString(prefix + "segundo_apellido"));
        teacher.setPhone(rs.getString(prefix + "telefono"));
        teacher.setStatus(DbEnumLookup.fromDbLabel(TeacherStatus.class, rs.getString(prefix + "estado")));
        return teacher;
    }

    /** Needs the columns of profesores, materias, grados and asignaciones_profesor. */
    static TeachingAssignment assignment(ResultSet rs) throws SQLException {
        TeachingAssignment assignment = new TeachingAssignment(teacher(rs), subject(rs),
                rs.getInt("anio_academico"), rs.getString("seccion"));
        assignment.setId(rs.getInt("id_asignacion"));
        return assignment;
    }

    /** Needs the assignment columns plus the ones of horarios_asignacion. */
    static ScheduleSlot slot(ResultSet rs) throws SQLException {
        ScheduleSlot slot = new ScheduleSlot(assignment(rs),
                DbEnumLookup.fromDbLabel(Weekday.class, rs.getString("dia_semana")),
                time(rs, "hora_inicio"), time(rs, "hora_fin"));
        slot.setId(rs.getInt("id_horario"));
        return slot;
    }

    /**
     * Needs the columns of estudiantes, materias and grados plus the ones of registros_academicos
     * and the aliases teacher_id and teacher_name (the teacher is not loaded as a whole entity).
     */
    static AcademicRecord academicRecord(ResultSet rs) throws SQLException {
        AcademicRecord record = new AcademicRecord(student(rs), subject(rs), rs.getInt("anio_academico"),
                DbEnumLookup.fromDbLabel(AcademicPeriod.class, rs.getString("periodo_academico")),
                rs.getBigDecimal("valor_nota"), rs.getInt("teacher_id"), rs.getString("teacher_name"),
                date(rs, "fecha_registro"));
        record.setId(rs.getInt("id_registro"));
        record.setObservations(rs.getString("observaciones"));
        return record;
    }

    /**
     * One row of the attendance SELECT: student and subject columns plus the attendance columns and
     * the aliases teacher_id and teacher_name.
     */
    static AttendanceRecord attendanceRecord(ResultSet rs) throws SQLException {
        AttendanceRecord record = new AttendanceRecord(student(rs), subject(rs), date(rs, "fecha_asistencia"),
                DbEnumLookup.fromDbLabel(AttendanceStatus.class, rs.getString("estado_asistencia")),
                rs.getInt("teacher_id"), rs.getString("teacher_name"));
        record.setId(rs.getInt("id_asistencia"));
        record.setObservations(rs.getString("observaciones"));
        return record;
    }

    /** Needs the columns of clubes (alias c) and of profesores (alias p, the teacher in charge). */
    static Club club(ResultSet rs) throws SQLException {
        return club(rs, "");
    }

    /** The teacher columns of the query have this prefix (empty when the query has no student columns). */
    static Club club(ResultSet rs, String teacherPrefix) throws SQLException {
        Club club = new Club(rs.getString("nombre_club"), rs.getString("descripcion"), teacher(rs, teacherPrefix),
                rs.getInt("capacidad_maxima"), rs.getString("horario_reunion"));
        club.setId(rs.getInt("id_club"));
        club.setActive(rs.getBoolean("esta_activo"));
        return club;
    }

    /** Needs the student columns, the club columns, the teacher columns with prefix t_ and the ones of membresias_club (alias m;
     * its joining date is selected as fecha_ingreso_club because the student also has a fecha_ingreso). */
    static ClubMembership membership(ResultSet rs) throws SQLException {
        ClubMembership membership = new ClubMembership(student(rs), club(rs, JdbcClubMembershipDao.TEACHER_PREFIX), date(rs, "fecha_ingreso_club"));
        membership.setId(rs.getInt("id_membresia"));
        if (MembershipStatus.fromDbLabel(rs.getString("estado_membresia")) == MembershipStatus.WITHDRAWN) {
            membership.withdraw(date(rs, "fecha_retiro"), rs.getString("motivo_retiro"));
        }
        return membership;
    }

    /** Needs the columns of eventos (alias ev) and the organizer's columns with the given prefix (see JdbcTeacherDao.teacherColumnsPrefixed). */
    static SchoolEvent event(ResultSet rs, String teacherPrefix) throws SQLException {
        SchoolEvent event = new SchoolEvent(rs.getString("nombre_evento"), rs.getString("descripcion"),
                EventType.fromDbLabel(rs.getString("tipo_evento")), date(rs, "fecha_evento"), rs.getString("lugar"),
                rs.getBoolean("tiene_competencia"), teacher(rs, teacherPrefix));
        event.setId(rs.getInt("id_evento"));
        event.setActive(rs.getBoolean("esta_activo"));
        return event;
    }

    /** Needs the student columns, the event columns, the organizer with prefix t_ and posicion_obtenida / id_participacion. */
    static EventParticipation participation(ResultSet rs) throws SQLException {
        EventParticipation participation = new EventParticipation(
                event(rs, JdbcEventParticipationDao.TEACHER_PREFIX), student(rs));
        participation.setId(rs.getInt("id_participacion"));
        String position = rs.getString("posicion_obtenida");
        if (position != null) {
            participation.assignPosition(EventPosition.fromDbLabel(position));
        }
        return participation;
    }

    private static LocalTime time(ResultSet rs, String column) throws SQLException {
        Time value = rs.getTime(column);
        return value.toLocalTime();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
