package cl.bancoxyz.bff.atm.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Segundo factor propio del canal cajeros: además del token OAuth2 (client_credentials, 2 min de vida),
 * cada petición debe venir de un terminal registrado (cabecera X-Terminal-Id).
 * Un terminal desconocido se bloquea y genera una alerta de seguridad en Kafka.
 */
@Component
public class TerminalAutorizadoFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Terminal-Id";

    private final Set<String> autorizados;
    private final PublicadorAlertas alertas;

    public TerminalAutorizadoFilter(@Value("${atm.terminales-autorizadas:}") String terminales, PublicadorAlertas alertas) {
        this.autorizados = Arrays.stream(terminales.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.alertas = alertas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/atm/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String terminal = request.getHeader(CABECERA);
        if (terminal == null || !autorizados.contains(terminal)) {
            alertas.publicar("TERMINAL_NO_AUTORIZADO", "ALTO", null, null,
                    "Intento de operación desde terminal no registrado: " + terminal + " (IP " + request.getRemoteAddr() + ")");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"TERMINAL_NO_AUTORIZADO\",\"codigo\":\"TERMINAL_NO_AUTORIZADO\",\"status\":403}");
            return;
        }
        chain.doFilter(request, response);
    }
}
