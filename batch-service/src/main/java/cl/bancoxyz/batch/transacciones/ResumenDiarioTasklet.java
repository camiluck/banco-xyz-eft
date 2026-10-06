package cl.bancoxyz.batch.transacciones;

import cl.bancoxyz.batch.comun.ReporteCsv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

/**
 * Paso 2: genera el resumen por día (créditos, débitos, neto y cantidad de anomalías)
 * y exporta los reportes CSV. Es idempotente: si el paso se reejecuta, primero borra
 * el resumen anterior de la misma instancia.
 */
public class ResumenDiarioTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ResumenDiarioTasklet.class);

    private final JdbcTemplate jdbc;
    private final long instanciaId;
    private final Path carpetaSalida;

    public ResumenDiarioTasklet(JdbcTemplate jdbc, long instanciaId, Path carpetaSalida) {
        this.jdbc = jdbc;
        this.instanciaId = instanciaId;
        this.carpetaSalida = carpetaSalida;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        jdbc.update("DELETE FROM resumen_transacciones_diario WHERE instancia_id = ?", instanciaId);
        int dias = jdbc.update("""
                INSERT INTO resumen_transacciones_diario
                    (instancia_id, fecha, total_registros, cantidad_creditos, cantidad_debitos,
                     monto_creditos, monto_debitos, saldo_neto, anomalias)
                SELECT instancia_id, fecha, COUNT(*),
                       SUM(CASE WHEN es_anomalia = FALSE AND tipo = 'credito' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN es_anomalia = FALSE AND tipo = 'debito'  THEN 1 ELSE 0 END),
                       SUM(CASE WHEN es_anomalia = FALSE AND tipo = 'credito' THEN monto ELSE 0 END),
                       SUM(CASE WHEN es_anomalia = FALSE AND tipo = 'debito'  THEN monto ELSE 0 END),
                       SUM(CASE WHEN es_anomalia = FALSE AND tipo = 'credito' THEN monto
                                WHEN es_anomalia = FALSE AND tipo = 'debito'  THEN -monto ELSE 0 END),
                       SUM(CASE WHEN es_anomalia = TRUE THEN 1 ELSE 0 END)
                FROM transaccion_diaria
                WHERE instancia_id = ?
                GROUP BY instancia_id, fecha""", instanciaId);
        contribution.incrementWriteCount(dias);

        Path resumen = ReporteCsv.exportar(jdbc, carpetaSalida.resolve("reporte_transacciones_diarias_" + instanciaId + ".csv"),
                """
                SELECT fecha, total_registros, cantidad_creditos, cantidad_debitos, monto_creditos, monto_debitos,
                       saldo_neto, anomalias
                FROM resumen_transacciones_diario WHERE instancia_id = ? ORDER BY fecha""", instanciaId);
        Path anomalias = ReporteCsv.exportar(jdbc, carpetaSalida.resolve("anomalias_transacciones_" + instanciaId + ".csv"),
                """
                SELECT archivo, linea, registro_id, fecha, monto, tipo, motivo_anomalia
                FROM transaccion_diaria WHERE instancia_id = ? AND es_anomalia = TRUE ORDER BY archivo, linea""", instanciaId);
        log.info("Resumen diario: {} día(s). Reportes: {} y {}", dias, resumen, anomalias);
        return RepeatStatus.FINISHED;
    }
}
