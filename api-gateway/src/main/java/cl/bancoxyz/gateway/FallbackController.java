package cl.bancoxyz.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Comportamiento alternativo cuando un microservicio no responde o su circuito está abierto:
 * en vez de un error genérico/timeout se responde rápido con 503 y un mensaje claro.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{servicio}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable String servicio) {
        Map<String, Object> cuerpo = Map.of(
                "error", "SERVICIO_NO_DISPONIBLE",
                "servicio", servicio,
                "mensaje", "El servicio no está disponible en este momento. Intente nuevamente en unos segundos.",
                "timestamp", Instant.now().toString());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(cuerpo));
    }
}
