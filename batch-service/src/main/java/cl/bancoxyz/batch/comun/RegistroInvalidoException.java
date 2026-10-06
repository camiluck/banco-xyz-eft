package cl.bancoxyz.batch.comun;

/** Registro con datos que no se pueden corregir: se omite (skip) y queda auditado en registro_rechazado. */
public class RegistroInvalidoException extends RuntimeException {

    private final String codigo;

    public RegistroInvalidoException(String codigo, String detalle) {
        super(codigo + ": " + detalle);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
