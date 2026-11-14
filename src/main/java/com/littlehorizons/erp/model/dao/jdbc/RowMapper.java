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
        Teacher teacher = new Teacher(
                DbEnumLookup.fromDbLabel(DocumentType.class, rs.getString("tipo_documento")),
                rs.getString("numero_documento"),
                rs.getString("primer_nombre"),
                rs.getString("primer_apellido"),
                rs.getString("correo_electronico"),
                rs.getString("especializacion"),
                date(rs, "fecha_contratacion"));
        teacher.setId(rs.getInt("id_profesor"));
        teacher.setMiddleName(rs.getString("segundo_nombre"));
        teacher.setSecondLastName(rs.getString("segundo_apellido"));
        teacher.setPhone(rs.getString("telefono"));
        teacher.setStatus(DbEnumLookup.fromDbLabel(TeacherStatus.class, rs.getString("estado")));
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

    private static LocalTime time(ResultSet rs, String column) throws SQLException {
        Time value = rs.getTime(column);
        return value.toLocalTime();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
