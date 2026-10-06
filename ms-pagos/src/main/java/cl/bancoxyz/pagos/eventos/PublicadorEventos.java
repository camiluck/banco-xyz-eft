package cl.bancoxyz.pagos.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Productor Kafka de ms-pagos. */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public PublicadorEventos(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    /** La clave es la referencia: todos los eventos de un mismo pago caen en la misma partición (orden garantizado). */
    public void transaccionCompletada(Eventos.TransaccionCompletadaEvento evento) {
        enviar(Eventos.TOPICO_TRANSACCIONES, evento.referencia(), evento);
    }

    public void alerta(Eventos.AlertaSeguridadEvento evento) {
        enviar(Eventos.TOPICO_ALERTAS, String.valueOf(evento.cuentaId()), evento);
    }

    private void enviar(String topico, String clave, Object evento) {
        try {
            String json = mapper.writeValueAsString(evento);
            kafka.send(topico, clave, json).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.error("No se pudo publicar en {}: {}", topico, ex.getMessage());
                } else {
                    log.info("Evento publicado en {}: {}", topico, json);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Error serializando evento {}", evento, e);
        } catch (RuntimeException e) {
            log.error("Kafka no disponible, evento no publicado en {}: {}", topico, e.getMessage());
        }
    }

    @Configuration
    static class Topicos {
        @Bean
        NewTopic topicoTransacciones() {
            return TopicBuilder.name(Eventos.TOPICO_TRANSACCIONES).partitions(3).replicas(1).build();
        }
    }
}
