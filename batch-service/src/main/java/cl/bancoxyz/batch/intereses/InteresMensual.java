package cl.bancoxyz.batch.intereses;

import java.math.BigDecimal;

public record InteresMensual(long instanciaId, String archivo, int linea, String periodo, long cuentaId, String nombre,
                             String tipo, int edad, BigDecimal saldo, BigDecimal tasaMensual, BigDecimal interes,
                             BigDecimal saldoProyectado, String hashRegistro) {
}
