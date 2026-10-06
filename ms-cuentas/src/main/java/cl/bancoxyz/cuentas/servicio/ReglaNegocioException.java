package cl.bancoxyz.cuentas.servicio;

import org.springframework.http.HttpStatus;

/** Error de negocio con su código HTTP (ej: SALDO_INSUFICIENTE -> 422). */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    public ReglaNegocioException(String codigo, String mensaje, HttpStatus status) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() { return codigo; }
    public HttpStatus getStatus() { return status; }

    public static ReglaNegocioException noEncontrada(Long id) {
        return new ReglaNegocioException("CUENTA_NO_ENCONTRADA", "No existe la cuenta " + id, HttpStatus.NOT_FOUND);
    }
}
