package com.littlehorizons.erp.model.enums;

/** How a payment was made, mirroring the metodo_pago ENUM. */
public enum PaymentMethod implements DbEnum {

    CASH("Efectivo"),
    TRANSFER("Transferencia"),
    CARD("Tarjeta");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @Override
    public String getDbLabel() {
        return label;
    }

    @Override
    public String getDisplayName() {
        return label;
    }

    public static PaymentMethod fromDbLabel(String label) {
        return DbEnumLookup.fromDbLabel(PaymentMethod.class, label);
    }
}
