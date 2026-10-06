package cl.bancoxyz.batch.estados;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoAnual(long instanciaId, String archivo, int linea, long cuentaId, LocalDate fecha, int anio,
                              String transaccion, BigDecimal monto, String montoOriginal, String descripcion,
                              boolean ajustado) {
}
