package cl.bancoxyz.bff.web.config;

import org.springframework.beans.factory.annotation.Value;
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
 * Cliente HTTP del BFF hacia el API Gateway:
 *  - @LoadBalanced: "http://api-gateway" se resuelve vía Eureka y se balancea entre réplicas.
 *  - Token de servicio (client_credentials, scope banco.api) obtenido y renovado automáticamente.
 *    El token del usuario/canal NUNCA se reenvía a los microservicios internos.
 */
@Configuration
public class ClienteHttpConfig {

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
        factory.setReadTimeout(6000);
        return configurer.configure(RestClient.builder()).requestFactory(factory);
    }

    @Bean
    OAuth2AuthorizedClientManager gestorTokens(ClientRegistrationRepository registros,
                                              OAuth2AuthorizedClientService servicio) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registros, servicio);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient gatewayRestClient(@LoadBalanced RestClient.Builder restClientBuilderBalanceado,
                                 OAuth2AuthorizedClientManager gestorTokens,
                                 @Value("${bff.gateway-url:http://api-gateway}") String gatewayUrl) {
        return restClientBuilderBalanceado.clone()
                .baseUrl(gatewayUrl)
                .requestInterceptor(interceptorToken(gestorTokens))
                .build();
    }

    static ClientHttpRequestInterceptor interceptorToken(OAuth2AuthorizedClientManager gestorTokens) {
        Authentication servicio = new AnonymousAuthenticationToken("bff-web", "bff-web-svc",
                AuthorityUtils.createAuthorityList("ROLE_SERVICIO"));
        return (request, body, execution) -> {
            OAuth2AuthorizedClient cliente = gestorTokens.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId("banco-api").principal(servicio).build());
            if (cliente == null) {
                throw new IllegalStateException("No se pudo obtener token de servicio desde auth-server");
            }
            request.getHeaders().setBearerAuth(cliente.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        };
    }
}
