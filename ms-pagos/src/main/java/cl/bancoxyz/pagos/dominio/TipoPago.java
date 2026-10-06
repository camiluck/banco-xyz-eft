package cl.bancoxyz.pagos.dominio;

public enum TipoPago {
    /** Débito en origen + crédito en destino */
    TRANSFERENCIA(true, true),
    /** Pago de cuentas/servicios: sólo débito en origen */
    PAGO_SERVICIO(true, false),
    /** Depósito: sólo crédito en destino */
    DEPOSITO(false, true),
    /** Retiro (cajero/caja): sólo débito en origen */
    RETIRO(true, false);

    private final boolean debita;
    private final boolean acredita;

    TipoPago(boolean debita, boolean acredita) {
        this.debita = debita;
        this.acredita = acredita;
    }

    public boolean debita() { return debita; }
    public boolean acredita() { return acredita; }
}
