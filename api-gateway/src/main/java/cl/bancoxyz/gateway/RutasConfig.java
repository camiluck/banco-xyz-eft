package cl.bancoxyz.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;

/**
 * Enrutamiento y balanceo de carga.
 * "lb://nombre-servicio" hace que Spring Cloud LoadBalancer reparta las peticiones
 * entre todas las instancias registradas en Eureka (round robin).
 */
@Configuration
public class RutasConfig {

    @Bean
    RouteLocator rutas(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("ms-cuentas", r -> r.path("/api/cuentas/**")
                        .filters(f -> f
                                .circuitBreaker(c -> {
                                    c.setName("cuentasCB");
                                    c.setFallbackUri("forward:/fallback/ms-cuentas");
                                    c.addStatusCode("503");
                                })
                                .retry(rc -> {
                                    rc.setRetries(2);
                                    rc.setMethods(HttpMethod.GET);
                                }))
                        .uri("lb://ms-cuentas"))
                .route("ms-pagos", r -> r.path("/api/pagos/**")
                        .filters(f -> f
                                .circuitBreaker(c -> {
                                    c.setName("pagosCB");
                                    c.setFallbackUri("forward:/fallback/ms-pagos");
                                    c.addStatusCode("503");
                                })
                                .retry(rc -> {
                                    rc.setRetries(2);
                                    rc.setMethods(HttpMethod.GET);
                                }))
                        .uri("lb://ms-pagos"))
                .route("ms-clientes", r -> r.path("/api/clientes/**")
                        .filters(f -> f
                                .circuitBreaker(c -> {
                                    c.setName("clientesCB");
                                    c.setFallbackUri("forward:/fallback/ms-clientes");
                                    c.addStatusCode("503");
                                })
                                .retry(rc -> {
                                    rc.setRetries(2);
                                    rc.setMethods(HttpMethod.GET);
                                }))
                        .uri("lb://ms-clientes"))
                .build();
    }

    /** Configuración por defecto de los circuit breakers del gateway. */
    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerPorDefecto() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .build());
    }
}
