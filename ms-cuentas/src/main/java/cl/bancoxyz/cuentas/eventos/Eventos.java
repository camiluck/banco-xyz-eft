package cl.bancoxyz.cuentas.eventos;

import java.time.Instant;

/** Eventos de dominio publicados en Kafka por ms-cuentas. */
public final class Eventos {

    private Eventos() {
    }

    public static final String TOPICO_CUENTAS = "cuentas-eventos";
    public static final String TOPICO_ALERTAS = "alertas-seguridad";

    /** tipo: CUENTA_ABIERTA, CUENTA_CERRADA, CUENTA_BLOQUEADA, CUENTA_ACTIVADA */
    public record CuentaEvento(String tipo, Long cuentaId, String numero, Long clienteId, Instant fecha) {
    }

    /** nivel: BAJO, MEDIO, ALTO */
    public record AlertaSeguridadEvento(String tipo, String nivel, Long clienteId, Long cuentaId,
                                        String origen, String detalle, Instant fecha) {
    }
}
