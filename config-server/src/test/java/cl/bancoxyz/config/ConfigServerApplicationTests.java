package cl.bancoxyz.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void entregaConfiguracionDeMsCuentas() {
        String body = rest.getForObject("/ms-cuentas/default", String.class);
        assertThat(body).contains("ms-cuentas.yml").contains("server.port");
    }
}
