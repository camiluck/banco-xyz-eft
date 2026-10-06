package cl.bancoxyz.bff.atm.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Productor Kafka: publica alertas de seguridad detectadas en los cajeros (tópico alertas-seguridad). */
@Component
public class PublicadorAlertas {

    private static final Logger log = LoggerFactory.getLogger(PublicadorAlertas.class);
    public static final String TOPICO = "alertas-seguridad";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public PublicadorAlertas(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public record AlertaSeguridadEvento(String tipo, String nivel, Long clienteId, Long cuentaId, String origen,
                                        String detalle, Instant fecha) {
    }

    public void publicar(String tipo, String nivel, Long clienteId, Long cuentaId, String detalle) {
        try {
            String json = mapper.writeValueAsString(
                    new AlertaSeguridadEvento(tipo, nivel, clienteId, cuentaId, "bff-atm", detalle, Instant.now()));
            kafka.send(TOPICO, String.valueOf(cuentaId), json).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("No se pudo publicar alerta {}: {}", tipo, ex.getMessage());
                }
            });
            log.warn("Alerta de seguridad publicada: {} - {}", tipo, detalle);
        } catch (Exception e) {
            log.error("Kafka no disponible, alerta {} no publicada: {}", tipo, e.getMessage());
        }
    }
}
