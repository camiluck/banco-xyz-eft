package cl.bancoxyz.pagos.eventos;

import java.math.BigDecimal;
import java.time.Instant;

public final class Eventos {

    private Eventos() {
    }

    public static final String TOPICO_TRANSACCIONES = "transacciones-completadas";
    public static final String TOPICO_ALERTAS = "alertas-seguridad";
    public static final String TOPICO_CUENTAS = "cuentas-eventos";

    public record TransaccionCompletadaEvento(String referencia, String tipo, BigDecimal monto, Long cuentaOrigenId,
                                              Long cuentaDestinoId, Long clienteOrigenId, Long clienteDestinoId,
                                              String canal, Instant fecha) {
    }

    public record AlertaSeguridadEvento(String tipo, String nivel, Long clienteId, Long cuentaId, String origen,
                                        String detalle, Instant fecha) {
    }
}
