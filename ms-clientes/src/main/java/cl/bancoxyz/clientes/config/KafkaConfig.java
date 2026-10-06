package cl.bancoxyz.clientes.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de errores de consumo: se reintenta 3 veces (1 s entre intentos) y, si sigue fallando,
 * el mensaje se envía a un tópico "<topico>.DLT" (Dead Letter Topic) para revisión manual,
 * sin detener el procesamiento del resto de los mensajes.
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        // Un JSON mal formado nunca se va a arreglar reintentando
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
