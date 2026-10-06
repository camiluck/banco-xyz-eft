package cl.bancoxyz.cuentas.eventos;

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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Productor Kafka. Los eventos se envían sólo DESPUÉS de que la transacción de base de datos
 * se confirma (AFTER_COMMIT), así nunca se publica un evento de algo que no quedó guardado.
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public PublicadorEventos(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCuentaEvento(Eventos.CuentaEvento evento) {
        enviar(Eventos.TOPICO_CUENTAS, String.valueOf(evento.cuentaId()), evento);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlerta(Eventos.AlertaSeguridadEvento evento) {
        enviar(Eventos.TOPICO_ALERTAS, String.valueOf(evento.cuentaId()), evento);
    }

    private void enviar(String topico, String clave, Object evento) {
        try {
            String json = mapper.writeValueAsString(evento);
            kafka.send(topico, clave, json).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.error("No se pudo publicar evento en {}: {}", topico, ex.getMessage());
                } else {
                    log.info("Evento publicado en {} (particion {}): {}", topico,
                            res.getRecordMetadata().partition(), json);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Error serializando evento {}", evento, e);
        } catch (RuntimeException e) {
            // Kafka caído: la operación de negocio ya se confirmó, sólo se registra el problema
            log.error("Kafka no disponible, evento no publicado en {}: {}", topico, e.getMessage());
        }
    }

    @Configuration
    static class Topicos {
        @Bean
        NewTopic topicoCuentas() {
            return TopicBuilder.name(Eventos.TOPICO_CUENTAS).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic topicoAlertas() {
            return TopicBuilder.name(Eventos.TOPICO_ALERTAS).partitions(3).replicas(1).build();
        }
    }
}
