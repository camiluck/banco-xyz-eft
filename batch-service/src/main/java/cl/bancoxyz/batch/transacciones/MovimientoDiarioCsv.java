package cl.bancoxyz.batch.transacciones;

import cl.bancoxyz.batch.comun.RegistroCsv;

/** Línea de movimientos_financieros_diarios.csv tal como viene del sistema legacy. */
public class MovimientoDiarioCsv extends RegistroCsv {

    public static final String[] CAMPOS = {"id", "fecha", "monto", "tipo"};

    private final String id;
    private final String fecha;
    private final String monto;
    private final String tipo;

    public MovimientoDiarioCsv(String id, String fecha, String monto, String tipo) {
        this.id = id;
        this.fecha = fecha;
        this.monto = monto;
        this.tipo = tipo;
    }

    public String getId() { return id; }
    public String getFecha() { return fecha; }
    public String getMonto() { return monto; }
    public String getTipo() { return tipo; }

    @Override
    public String contenido() {
        return String.join(",", id, fecha, monto, tipo);
    }
}
