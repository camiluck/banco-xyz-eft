package cl.bancoxyz.bff.web.web;

import org.springframework.http.HttpStatus;

/** Regla propia del canal (límites, formatos, etc.). */
public class ReglaCanalException extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    public ReglaCanalException(String codigo, String mensaje, HttpStatus status) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() { return codigo; }
    public HttpStatus getStatus() { return status; }
}
