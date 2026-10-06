package cl.bancoxyz.pagos.servicio;

import cl.bancoxyz.pagos.dominio.EstadoPago;
import cl.bancoxyz.pagos.dominio.Pago;
import cl.bancoxyz.pagos.dominio.TipoPago;
import cl.bancoxyz.pagos.eventos.Eventos;
import cl.bancoxyz.pagos.eventos.PublicadorEventos;
import cl.bancoxyz.pagos.integracion.CuentasClient;
import cl.bancoxyz.pagos.integracion.CuentasClient.MovimientoCuenta;
import cl.bancoxyz.pagos.integracion.CuentasNoDisponibleException;
import cl.bancoxyz.pagos.repositorio.PagoRepository;
import cl.bancoxyz.pagos.web.Dtos.OperacionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta cada operación como una pequeña SAGA:
 *   1. Se registra el pago como PENDIENTE.
 *   2. Se aplica el débito en la cuenta origen (ms-cuentas, idempotente con referencia + "-D").
 *   3. Se aplica el crédito en la cuenta destino (referencia + "-C").
 *   4. Si el destino rechaza el crédito, se COMPENSA devolviendo el débito (referencia + "-R").
 *   5. Al completar se publica "transacciones-completadas" en Kafka.
 * Si ms-cuentas no está disponible el pago queda PENDIENTE y el planificador lo retoma.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagos;
    private final CuentasClient cuentas;
    private final PublicadorEventos eventos;
    private final ObjectMapper mapper;
    private final BigDecimal montoAlerta;
    private final int maxIntentos;

    public PagoService(PagoRepository pagos, CuentasClient cuentas, PublicadorEventos eventos, ObjectMapper mapper,
                       @Value("${pagos.monto-alerta:5000000}") BigDecimal montoAlerta,
                       @Value("${pagos.reintento.max-intentos:5}") int maxIntentos) {
        this.pagos = pagos;
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.mapper = mapper;
        this.montoAlerta = montoAlerta;
        this.maxIntentos = maxIntentos;
    }

    /** Resultado: el pago y si ya existía (llamada repetida con la misma Idempotency-Key). */
    public record Resultado(Pago pago, boolean repetido) {
    }

    public Resultado registrar(TipoPago tipo, OperacionRequest req, String idempotencyKey) {
        validar(tipo, req);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Pago> previo = pagos.findByReferencia(idempotencyKey);
            if (previo.isPresent()) {
                log.info("Operación {} ya registrada (Idempotency-Key), se devuelve el resultado original", idempotencyKey);
                return new Resultado(previo.get(), true);
            }
        }
        String referencia = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey : "PAG-" + UUID.randomUUID();
        String canal = req.canal() == null ? "API" : req.canal().toUpperCase();
        Pago pago = pagos.save(new Pago(referencia, tipo,
                tipo.debita() ? req.cuentaOrigenId() : null,
                tipo.acredita() ? req.cuentaDestinoId() : null,
                req.monto(), req.descripcion(), canal));
        log.info("Pago {} ({}) registrado por {} desde canal {}", referencia, tipo, req.monto(), canal);
        return new Resultado(ejecutar(pago), false);
    }

    public Pago obtener(String referencia) {
        return pagos.findByReferencia(referencia).orElseThrow(() -> new ReglaNegocioException(
                "PAGO_NO_ENCONTRADO", "No existe la operación " + referencia, HttpStatus.NOT_FOUND));
    }

    public List<Pago> deCuenta(Long cuentaId, int limite) {
        return pagos.deCuenta(cuentaId, PageRequest.of(0, Math.min(limite, 100)));
    }

    /** Ejecuta (o retoma) los pasos pendientes de un pago. Seguro de llamar varias veces. */
    public Pago ejecutar(Pago pago) {
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            return pago;
        }
        pago.registrarIntento();
        String ref = pago.getReferencia();
        String desc = descripcion(pago);
        try {
            if (pago.faltaDebito()) {
                MovimientoCuenta debito = cuentas.aplicarMovimiento(pago.getCuentaOrigenId(), "DEBITO",
                        pago.getMonto(), ref + "-D", desc);
                pago.marcarDebito(debito.clienteId());
                pago = pagos.save(pago);
            }
            if (pago.faltaCredito()) {
                try {
                    MovimientoCuenta credito = cuentas.aplicarMovimiento(pago.getCuentaDestinoId(), "CREDITO",
                            pago.getMonto(), ref + "-C", desc);
                    pago.marcarCredito(credito.clienteId());
                } catch (HttpClientErrorException rechazoDestino) {
                    if (pago.isDebitoAplicado()) {
                        return compensar(pago, motivo(rechazoDestino));
                    }
                    throw rechazoDestino;
                }
            }
            pago.completar();
            pago = pagos.save(pago);
            log.info("Pago {} COMPLETADO", ref);
            publicarCompletado(pago);
            return pago;
        } catch (HttpClientErrorException rechazo) {
            pago.rechazar(motivo(rechazo));
            log.info("Pago {} RECHAZADO: {}", ref, pago.getMotivo());
            return pagos.save(pago);
        } catch (CuentasNoDisponibleException caido) {
            if (!pago.isDebitoAplicado() && pago.getIntentos() >= maxIntentos) {
                pago.rechazar("Servicio de cuentas no disponible tras " + pago.getIntentos() + " intentos");
                log.warn("Pago {} RECHAZADO por indisponibilidad prolongada", ref);
            } else {
                pago.anotar("En proceso: servicio de cuentas no disponible, se reintentará automáticamente");
                log.warn("Pago {} queda PENDIENTE (intento {})", ref, pago.getIntentos());
            }
            return pagos.save(pago);
        }
    }

    /** Transacción compensatoria: devuelve el dinero debitado a la cuenta origen. */
    private Pago compensar(Pago pago, String motivo) {
        log.warn("Destino rechazó el crédito de {} ({}). Compensando débito...", pago.getReferencia(), motivo);
        cuentas.aplicarMovimiento(pago.getCuentaOrigenId(), "CREDITO", pago.getMonto(),
                pago.getReferencia() + "-R", "Reverso: " + motivo);
        pago.revertir("Revertido: " + motivo);
        return pagos.save(pago);
    }

    private void publicarCompletado(Pago p) {
        eventos.transaccionCompletada(new Eventos.TransaccionCompletadaEvento(p.getReferencia(), p.getTipo().name(),
                p.getMonto(), p.getCuentaOrigenId(), p.getCuentaDestinoId(), p.getClienteOrigenId(),
                p.getClienteDestinoId(), p.getCanal(), Instant.now()));
        if (p.getMonto().compareTo(montoAlerta) >= 0) {
            Long cuenta = p.getCuentaOrigenId() != null ? p.getCuentaOrigenId() : p.getCuentaDestinoId();
            Long cliente = p.getClienteOrigenId() != null ? p.getClienteOrigenId() : p.getClienteDestinoId();
            eventos.alerta(new Eventos.AlertaSeguridadEvento("MONTO_INUSUAL", "MEDIO", cliente, cuenta, "ms-pagos",
                    "Operación %s por $%s supera el umbral de monitoreo".formatted(p.getTipo(), p.getMonto()),
                    Instant.now()));
        }
    }

    private static void validar(TipoPago tipo, OperacionRequest req) {
        if (tipo.debita() && req.cuentaOrigenId() == null) {
            throw new ReglaNegocioException("DATOS_INVALIDOS", "cuentaOrigenId es obligatoria para " + tipo, HttpStatus.BAD_REQUEST);
        }
        if (tipo.acredita() && req.cuentaDestinoId() == null) {
            throw new ReglaNegocioException("DATOS_INVALIDOS", "cuentaDestinoId es obligatoria para " + tipo, HttpStatus.BAD_REQUEST);
        }
        if (tipo == TipoPago.TRANSFERENCIA && req.cuentaOrigenId().equals(req.cuentaDestinoId())) {
            throw new ReglaNegocioException("DATOS_INVALIDOS", "La cuenta origen y destino no pueden ser la misma", HttpStatus.BAD_REQUEST);
        }
    }

    private static String descripcion(Pago p) {
        String base = switch (p.getTipo()) {
            case TRANSFERENCIA -> "Transferencia";
            case PAGO_SERVICIO -> "Pago de servicio";
            case DEPOSITO -> "Depósito";
            case RETIRO -> "Retiro";
        };
        return p.getDescripcion() == null || p.getDescripcion().isBlank() ? base : base + ": " + p.getDescripcion();
    }

    /** Extrae el código de negocio que devuelve ms-cuentas (ProblemDetail con propiedad "codigo"). */
    private String motivo(HttpClientErrorException ex) {
        try {
            JsonNode body = mapper.readTree(ex.getResponseBodyAsString());
            String codigo = body.path("codigo").asText(ex.getStatusCode().toString());
            String detalle = body.path("detail").asText("");
            return detalle.isBlank() ? codigo : codigo + ": " + detalle;
        } catch (Exception e) {
            return ex.getStatusCode().toString();
        }
    }
}
