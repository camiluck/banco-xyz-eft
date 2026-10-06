package cl.bancoxyz.batch.estados;

import cl.bancoxyz.batch.comun.RegistroCsv;

/** Línea de estados_financieros_anuales.csv tal como viene del sistema legacy. */
public class MovimientoAnualCsv extends RegistroCsv {

    public static final String[] CAMPOS = {"cuenta_id", "fecha", "transaccion", "monto", "descripcion"};

    private final String cuentaId;
    private final String fecha;
    private final String transaccion;
    private final String monto;
    private final String descripcion;

    public MovimientoAnualCsv(String cuentaId, String fecha, String transaccion, String monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.transaccion = transaccion;
        this.monto = monto;
        this.descripcion = descripcion;
    }

    public String getCuentaId() { return cuentaId; }
    public String getFecha() { return fecha; }
    public String getTransaccion() { return transaccion; }
    public String getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }

    @Override
    public String contenido() {
        return String.join(",", cuentaId, fecha, transaccion, monto, descripcion);
    }
}
