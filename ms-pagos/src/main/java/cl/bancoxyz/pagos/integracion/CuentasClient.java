package cl.bancoxyz.pagos.integracion;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Llamadas a ms-cuentas protegidas con Resilience4j:
 *  - Retry: hasta 3 intentos con backoff exponencial ante fallas temporales.
 *  - CircuitBreaker: si más del 50% de las llamadas fallan, deja de llamar por 15 s
 *    (respuesta inmediata en vez de esperar timeouts) y luego prueba de nuevo.
 *  - Fallback: convierte la falla técnica en CuentasNoDisponibleException; el pago queda
 *    PENDIENTE y el planificador lo reintenta. Los errores 4xx (de negocio) se propagan tal cual.
 */
@Component
public class CuentasClient {

    private static final Logger log = LoggerFactory.getLogger(CuentasClient.class);

    private final RestClient rest;

    public CuentasClient(@Qualifier("cuentasRestClient") RestClient rest) {
        this.rest = rest;
    }

    public record MovimientoRequest(String tipo, BigDecimal monto, String referencia, String descripcion) {
    }

    public record MovimientoCuenta(Long id, Long cuentaId, Long clienteId, String tipo, BigDecimal monto,
                                   BigDecimal saldoResultante, String referencia, boolean duplicado) {
    }

    @Retry(name = "cuentas", fallbackMethod = "cuentasNoDisponible")
    @CircuitBreaker(name = "cuentas")
    public MovimientoCuenta aplicarMovimiento(Long cuentaId, String tipo, BigDecimal monto, String referencia,
                                              String descripcion) {
        log.debug("Llamando a ms-cuentas: {} {} en cuenta {} ({})", tipo, monto, cuentaId, referencia);
        return rest.post()
                .uri("/api/cuentas/{id}/movimientos", cuentaId)
                .body(new MovimientoRequest(tipo, monto, referencia, descripcion))
                .retrieve()
                .body(MovimientoCuenta.class);
    }

    @SuppressWarnings("unused")
    private MovimientoCuenta cuentasNoDisponible(Long cuentaId, String tipo, BigDecimal monto, String referencia,
                                                 String descripcion, Throwable error) {
        if (error instanceof HttpClientErrorException negocio) {
            throw negocio;
        }
        log.warn("ms-cuentas no disponible para {} ({}): {}", referencia, tipo, error.toString());
        throw new CuentasNoDisponibleException("Servicio de cuentas no disponible", error);
    }
}
