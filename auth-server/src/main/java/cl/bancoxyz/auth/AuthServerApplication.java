package cl.bancoxyz.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servidor de autorización OAuth2 / OpenID Connect del Banco XYZ.
 * Emite tokens JWT firmados (RS256) para cada canal (web, móvil, cajeros)
 * y para la comunicación entre servicios (client_credentials).
 */
@SpringBootApplication
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
