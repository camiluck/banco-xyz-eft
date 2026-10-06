package cl.bancoxyz.bff.movil.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticación y autorización propias del canal movil:
 *  - Sólo se aceptan JWT firmados por auth-server, cuyo "aud" sea el cliente de este canal
 *    (spring.security.oauth2.resourceserver.jwt.audiences) y con el scope del canal.
 *  - HTTPS obligatorio (server.ssl) + cabecera HSTS.
 *  - Sin sesiones en servidor (stateless).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, @Value("${bff.scope-requerido:canal.movil}") String scope) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/movil/**").hasAuthority("SCOPE_" + scope)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
