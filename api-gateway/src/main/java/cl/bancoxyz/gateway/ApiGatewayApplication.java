package cl.bancoxyz.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway interno: punto único de entrada a los microservicios de negocio.
 * Enruta usando Eureka (lb://), balancea carga entre réplicas, valida el token JWT
 * y aplica circuit breaker + reintentos por ruta.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
