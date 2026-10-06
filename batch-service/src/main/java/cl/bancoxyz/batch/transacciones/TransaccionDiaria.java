package cl.bancoxyz.batch.transacciones;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Transacción validada y normalizada. Las anomalías se conservan marcadas para revisión. */
public record TransaccionDiaria(long instanciaId, String archivo, int linea, long registroId, LocalDate fecha,
                                BigDecimal monto, String tipo, String fechaOriginal, boolean formatoFechaCorregido,
                                boolean esAnomalia, String motivoAnomalia) {
}
