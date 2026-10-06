package cl.bancoxyz.bff.movil.web;

import cl.bancoxyz.bff.movil.integracion.ServicioNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ManejadorErrores {

    /** Error de negocio de un microservicio (404, 409, 422...): se traslada al frontend. */
    @ExceptionHandler(HttpClientErrorException.class)
    ResponseEntity<String> negocio(HttpClientErrorException ex) {
        String cuerpo = ex.getResponseBodyAsString();
        if (cuerpo.isBlank()) {
            cuerpo = "{\"codigo\":\"" + ex.getStatusCode().value() + "\"}";
        }
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }

    /** Comportamiento alternativo: servicio interno caído -> 503 claro y rápido. */
    @ExceptionHandler(ServicioNoDisponibleException.class)
    ProblemDetail noDisponible(ServicioNoDisponibleException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de " + ex.getServicio() + " no está disponible. Intente nuevamente en unos minutos.");
        pd.setTitle("SERVICIO_NO_DISPONIBLE");
        pd.setProperty("codigo", "SERVICIO_NO_DISPONIBLE");
        return pd;
    }

    @ExceptionHandler(ReglaCanalException.class)
    ProblemDetail reglaCanal(ReglaCanalException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getCodigo());
        pd.setProperty("codigo", ex.getCodigo());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        pd.setTitle("DATOS_INVALIDOS");
        pd.setProperty("codigo", "DATOS_INVALIDOS");
        return pd;
    }
}
