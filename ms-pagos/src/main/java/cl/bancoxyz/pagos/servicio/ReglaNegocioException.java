package cl.bancoxyz.pagos.servicio;

import org.springframework.http.HttpStatus;

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
}
