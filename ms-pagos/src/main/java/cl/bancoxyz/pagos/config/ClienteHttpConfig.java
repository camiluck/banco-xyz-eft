package cl.bancoxyz.pagos.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia ms-cuentas:
 *  - @LoadBalanced: resuelve "http://ms-cuentas" con Eureka y reparte entre réplicas.
 *  - Interceptor OAuth2: obtiene (y renueva) un token client_credentials de auth-server
 *    y lo envía como "Authorization: Bearer ...".
 */
@Configuration
public class ClienteHttpConfig {

    static final String REGISTRO_OAUTH = "banco-api";

    /** Builder normal (sin balanceo) para cualquier otro uso, p.ej. librerías de Spring Cloud. */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    @LoadBalanced
    RestClient.Builder restClientBuilderBalanceado(RestClientBuilderConfigurer configurer) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return configurer.configure(RestClient.builder()).requestFactory(factory);
    }

    /** Gestor de tokens para llamadas servicio-a-servicio (funciona también fuera de una petición HTTP). */
    @Bean
    OAuth2AuthorizedClientManager gestorTokens(ClientRegistrationRepository registros,
                                              OAuth2AuthorizedClientService servicio) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registros, servicio);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient cuentasRestClient(@LoadBalanced RestClient.Builder restClientBuilderBalanceado, OAuth2AuthorizedClientManager gestorTokens) {
        return restClientBuilderBalanceado.clone()
                .baseUrl("http://ms-cuentas")
                .requestInterceptor(interceptorToken(gestorTokens))
                .build();
    }

    static ClientHttpRequestInterceptor interceptorToken(OAuth2AuthorizedClientManager gestorTokens) {
        Authentication servicio = new AnonymousAuthenticationToken("ms-pagos", "ms-pagos-svc",
                AuthorityUtils.createAuthorityList("ROLE_SERVICIO"));
        return (request, body, execution) -> {
            OAuth2AuthorizedClient cliente = gestorTokens.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRO_OAUTH).principal(servicio).build());
            if (cliente == null) {
                throw new IllegalStateException("No se pudo obtener token de servicio desde auth-server");
            }
            request.getHeaders().setBearerAuth(cliente.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        };
    }
}
