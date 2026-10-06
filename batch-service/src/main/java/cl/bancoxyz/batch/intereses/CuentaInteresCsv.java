package cl.bancoxyz.batch.intereses;

import cl.bancoxyz.batch.comun.RegistroCsv;

/** Línea de intereses_trimestrales.csv tal como viene del sistema legacy. */
public class CuentaInteresCsv extends RegistroCsv {

    public static final String[] CAMPOS = {"cuenta_id", "nombre", "saldo", "edad", "tipo"};

    private final String cuentaId;
    private final String nombre;
    private final String saldo;
    private final String edad;
    private final String tipo;

    public CuentaInteresCsv(String cuentaId, String nombre, String saldo, String edad, String tipo) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.saldo = saldo;
        this.edad = edad;
        this.tipo = tipo;
    }

    public String getCuentaId() { return cuentaId; }
    public String getNombre() { return nombre; }
    public String getSaldo() { return saldo; }
    public String getEdad() { return edad; }
    public String getTipo() { return tipo; }

    @Override
    public String contenido() {
        return String.join(",", cuentaId, nombre, saldo, edad, tipo);
    }
}
