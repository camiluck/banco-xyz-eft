package cl.bancoxyz.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    WebTestClient client;

    @Test
    void rechazaPeticionSinToken() {
        client.get().uri("/api/cuentas/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void fallbackRespondeServicioNoDisponible() {
        client.get().uri("/fallback/ms-cuentas").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.error").isEqualTo("SERVICIO_NO_DISPONIBLE");
    }
}
