package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;

/**
 * The person who asks for a resource, seen only as far as a loan needs: who it is, how to reach
 * him or her and whether he or she is still part of the school. A student and a teacher are
 * different tables, so this small immutable record (a Java 21 {@code record}) lets the loan code
 * treat both in the same way. The rule "who may borrow" lives ONLY here, in {@code active}:
 * a retired, graduated or suspended student and an inactive or on-leave teacher may not.
 */
public record Borrower(BorrowerType type, int id, String fullName, String documentNumber, String contact,
                       String statusLabel, boolean active) {

    public static Borrower student(int id, String fullName, String documentNumber, String email, String phone,
                                   StudentStatus status) {
        return new Borrower(BorrowerType.STUDENT, id, fullName, documentNumber, contactOf(email, phone),
                status.getDisplayName(), status == StudentStatus.ACTIVE);
    }

    public static Borrower teacher(int id, String fullName, String documentNumber, String email, String phone,
                                   TeacherStatus status) {
        return new Borrower(BorrowerType.TEACHER, id, fullName, documentNumber, contactOf(email, phone),
                status.getDisplayName(), status == TeacherStatus.ACTIVE);
    }

    public static Borrower of(Student student) {
        return student(student.getId(), student.getFullName(), student.getDocumentNumber(), student.getEmail(),
                student.getPhone(), student.getStatus());
    }

    public static Borrower of(Teacher teacher) {
        return teacher(teacher.getId(), teacher.getFullName(), teacher.getDocumentNumber(), teacher.getEmail(),
                teacher.getPhone(), teacher.getStatus());
    }

    /** How to reach the person: the e-mail, or the phone when there is no e-mail; null when there is neither. */
    private static String contactOf(String email, String phone) {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return phone == null || phone.isBlank() ? null : phone;
    }

    /** Text for menus: the state is only shown when it is not "Activo", because that is what stops a loan. */
    public String label() {
        return fullName + " - " + documentNumber + (active ? "" : " [" + statusLabel + "]");
    }

    /** Text for messages: "Estudiante Ana Pérez (1234567)". */
    public String describe() {
        return type.getDisplayName() + " " + fullName + " (" + documentNumber + ")";
    }
}
