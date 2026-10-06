package cl.bancoxyz.clientes.eventos;

import cl.bancoxyz.clientes.servicio.ClienteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Consumidores Kafka: procesan en tiempo real los eventos publicados por otros servicios
 * y los transforman en notificaciones para el cliente.
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);
    private static final NumberFormat CLP = NumberFormat.getCurrencyInstance(Locale.of("es", "CL"));

    private final ClienteService servicio;
    private final ObjectMapper mapper;

    public ConsumidorEventos(ClienteService servicio, ObjectMapper mapper) {
        this.servicio = servicio;
        this.mapper = mapper;
    }

    /** Publicado por ms-pagos cuando un pago/transferencia/depósito/retiro termina OK. */
    @KafkaListener(topics = "transacciones-completadas", autoStartup = "${app.kafka.listeners:true}")
    public void transaccionCompletada(@Payload String json,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                                      @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        JsonNode e = mapper.readTree(json);
        String eventoId = topico + "-" + particion + "-" + offset;
        String tipo = e.path("tipo").asText();
        String monto = CLP.format(e.path("monto").asDouble());
        String ref = e.path("referencia").asText();
        log.info("Transacción completada recibida: {} {} ({})", tipo, monto, ref);

        Long origen = idONull(e, "clienteOrigenId");
        Long destino = idONull(e, "clienteDestinoId");
        if (origen != null) {
            servicio.notificar(origen, "TRANSACCION", "INFO",
                    "%s por %s realizada con éxito. Ref: %s".formatted(tipo, monto, ref), eventoId);
        }
        if (destino != null && !destino.equals(origen)) {
            servicio.notificar(destino, "TRANSACCION", "INFO",
                    "Recibiste %s (%s). Ref: %s".formatted(monto, tipo, ref), eventoId);
        }
    }

    /** Publicado por ms-cuentas (PIN bloqueado), ms-pagos (monto inusual) y bff-atm (terminal no autorizado). */
    @KafkaListener(topics = "alertas-seguridad", autoStartup = "${app.kafka.listeners:true}")
    public void alertaSeguridad(@Payload String json,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                                @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        JsonNode e = mapper.readTree(json);
        String eventoId = topico + "-" + particion + "-" + offset;
        log.warn("ALERTA DE SEGURIDAD [{}] {}: {}", e.path("nivel").asText(), e.path("tipo").asText(),
                e.path("detalle").asText());
        servicio.notificar(idONull(e, "clienteId"), "ALERTA_SEGURIDAD", e.path("nivel").asText("MEDIO"),
                "Alerta de seguridad: " + e.path("detalle").asText(), eventoId);
    }

    /** Publicado por ms-cuentas al abrir, cerrar, bloquear o activar cuentas. */
    @KafkaListener(topics = "cuentas-eventos", autoStartup = "${app.kafka.listeners:true}")
    public void eventoCuenta(@Payload String json,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                             @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        JsonNode e = mapper.readTree(json);
        String eventoId = topico + "-" + particion + "-" + offset;
        Long clienteId = idONull(e, "clienteId");
        String tipo = e.path("tipo").asText();
        String numero = e.path("numero").asText();
        if (clienteId == null) {
            return;
        }
        switch (tipo) {
            case "CUENTA_ABIERTA" -> servicio.ajustarCuentasActivas(clienteId, +1, eventoId);
            case "CUENTA_CERRADA" -> servicio.ajustarCuentasActivas(clienteId, -1, eventoId);
            default -> { }
        }
        String mensaje = switch (tipo) {
            case "CUENTA_ABIERTA" -> "Tu nueva cuenta " + numero + " ya está disponible";
            case "CUENTA_CERRADA" -> "La cuenta " + numero + " fue cerrada";
            case "CUENTA_BLOQUEADA" -> "La cuenta " + numero + " fue bloqueada por seguridad";
            case "CUENTA_ACTIVADA" -> "La cuenta " + numero + " fue reactivada";
            default -> "Cambio en la cuenta " + numero;
        };
        servicio.notificar(clienteId, "CUENTA", "INFO", mensaje, eventoId);
    }

    private static Long idONull(JsonNode e, String campo) {
        JsonNode n = e.get(campo);
        return n == null || n.isNull() ? null : n.asLong();
    }
}
