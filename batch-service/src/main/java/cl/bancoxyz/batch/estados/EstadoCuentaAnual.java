package cl.bancoxyz.batch.estados;

import java.math.BigDecimal;

/** Estado de cuenta anual consolidado por cuenta (para auditoría). */
public record EstadoCuentaAnual(long instanciaId, int anio, long cuentaId, BigDecimal totalDepositos,
                                BigDecimal totalRetiros, BigDecimal totalCompras, BigDecimal totalPagos,
                                BigDecimal totalCargos, BigDecimal saldoNeto, long cantidadMovimientos,
                                long movimientosAjustados) {
}
