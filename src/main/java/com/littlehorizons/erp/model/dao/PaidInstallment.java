package com.littlehorizons.erp.model.dao;

import java.math.BigDecimal;

/** How much a student has paid in total for one monthly installment of a year. */
public record PaidInstallment(int studentId, int month, BigDecimal total) {
}
