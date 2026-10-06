package cl.bancoxyz.pagos.integracion;

/** ms-cuentas no responde (caído, timeout o circuito abierto). La operación se reintentará más tarde. */
public class CuentasNoDisponibleException extends RuntimeException {
    public CuentasNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
