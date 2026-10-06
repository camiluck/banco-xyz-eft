package cl.bancoxyz.bff.movil.integracion;

import cl.bancoxyz.bff.movil.integracion.Modelos.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Acceso a los microservicios (a través del API Gateway) protegido con Resilience4j:
 * cada llamada pasa por Retry + CircuitBreaker con la configuración del servicio destino
 * ("clientes", "cuentas", "pagos"). Las fallas técnicas se convierten en ServicioNoDisponibleException,
 * que cada controlador maneja con un comportamiento alternativo (datos parciales, mensaje claro, etc.).
 */
@Component
public class BancoApiClient {

    private static final Logger log = LoggerFactory.getLogger(BancoApiClient.class);

    private final RestClient rest;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry reintentos;

    public BancoApiClient(@Qualifier("gatewayRestClient") RestClient rest, CircuitBreakerRegistry circuitBreakers,
                          RetryRegistry reintentos) {
        this.rest = rest;
        this.circuitBreakers = circuitBreakers;
        this.reintentos = reintentos;
    }

    // ----------------------------------------------------------------------------- clientes
    public ClienteDto cliente(Long id) {
        return llamar("clientes", () -> rest.get().uri("/api/clientes/{id}", id).retrieve().body(ClienteDto.class));
    }

    public ClienteDto actualizarCliente(Long id, Map<String, Object> cambios) {
        return llamar("clientes", () -> rest.put().uri("/api/clientes/{id}", id).body(cambios).retrieve()
                .body(ClienteDto.class));
    }

    public List<NotificacionDto> notificaciones(Long clienteId, int limite) {
        return llamar("clientes", () -> rest.get()
                .uri("/api/clientes/{id}/notificaciones?limite={l}", clienteId, limite)
                .retrieve().body(new ParameterizedTypeReference<List<NotificacionDto>>() { }));
    }

    // ----------------------------------------------------------------------------- cuentas
    public List<CuentaDto> cuentasDeCliente(Long clienteId) {
        return llamar("cuentas", () -> rest.get().uri("/api/cuentas?clienteId={id}", clienteId)
                .retrieve().body(new ParameterizedTypeReference<List<CuentaDto>>() { }));
    }

    public CuentaDto cuenta(Long cuentaId) {
        return llamar("cuentas", () -> rest.get().uri("/api/cuentas/{id}", cuentaId).retrieve().body(CuentaDto.class));
    }

    public SaldoDto saldo(Long cuentaId) {
        return llamar("cuentas", () -> rest.get().uri("/api/cuentas/{id}/saldo", cuentaId).retrieve().body(SaldoDto.class));
    }

    public List<MovimientoDto> movimientos(Long cuentaId, int limite) {
        return llamar("cuentas", () -> rest.get().uri("/api/cuentas/{id}/movimientos?limite={l}", cuentaId, limite)
                .retrieve().body(new ParameterizedTypeReference<List<MovimientoDto>>() { }));
    }

    public PinDto validarPin(Long cuentaId, String pin) {
        return llamar("cuentas", () -> rest.post().uri("/api/cuentas/{id}/validar-pin", cuentaId)
                .body(Map.of("pin", pin)).retrieve().body(PinDto.class));
    }

    // ----------------------------------------------------------------------------- pagos
    public List<PagoDto> operaciones(Long cuentaId, int limite) {
        return llamar("pagos", () -> rest.get().uri("/api/pagos?cuentaId={id}&limite={l}", cuentaId, limite)
                .retrieve().body(new ParameterizedTypeReference<List<PagoDto>>() { }));
    }

    /**
     * tipo: transferencias | servicios | depositos | retiros.
     * Siempre con Idempotency-Key: así un reintento automático nunca duplica la operación.
     * Un 422 (rechazado por negocio) NO es una excepción: se devuelve el pago con su motivo.
     */
    public PagoDto operar(String tipo, OperacionRequest req, String idempotencyKey) {
        return llamar("pagos", () -> rest.post().uri("/api/pagos/{tipo}", tipo)
                .header("Idempotency-Key", idempotencyKey)
                .body(req)
                .retrieve()
                .onStatus(s -> s.value() == HttpStatus.UNPROCESSABLE_ENTITY.value(), (rq, rs) -> { })
                .body(PagoDto.class));
    }

    // ----------------------------------------------------------------------------- resiliencia
    private <T> T llamar(String servicio, Supplier<T> llamada) {
        CircuitBreaker cb = circuitBreakers.circuitBreaker(servicio);
        Retry retry = reintentos.retry(servicio);
        Supplier<T> protegida = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(cb, llamada));
        try {
            return protegida.get();
        } catch (HttpClientErrorException errorNegocio) {
            throw errorNegocio; // 4xx: lo decide el negocio, se informa tal cual al frontend
        } catch (RuntimeException falla) {
            log.warn("Falla llamando a {} (circuito {}): {}", servicio, cb.getState(), falla.toString());
            throw new ServicioNoDisponibleException(servicio, falla);
        }
    }
}
