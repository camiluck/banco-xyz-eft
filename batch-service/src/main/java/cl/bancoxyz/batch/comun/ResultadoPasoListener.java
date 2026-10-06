package cl.bancoxyz.batch.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * Política de finalización: un paso que terminó bien pero omitió registros termina con el estado
 * de salida COMPLETADO_CON_OMISIONES (visible en el monitoreo y en la API), y deja en el log un
 * resumen de leídos / escritos / omitidos.
 */
public class ResultadoPasoListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ResultadoPasoListener.class);
    public static final String COMPLETADO_CON_OMISIONES = "COMPLETADO_CON_OMISIONES";

    @Override
    public ExitStatus afterStep(StepExecution se) {
        log.info("Paso {} terminado: estado={} leidos={} escritos={} filtrados={} omitidos={} commits={} rollbacks={}",
                se.getStepName(), se.getStatus(), se.getReadCount(), se.getWriteCount(), se.getFilterCount(),
                se.getSkipCount(), se.getCommitCount(), se.getRollbackCount());
        if (se.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode()) && se.getSkipCount() > 0) {
            return new ExitStatus(COMPLETADO_CON_OMISIONES,
                    se.getSkipCount() + " registro(s) omitido(s); ver tabla registro_rechazado");
        }
        return se.getExitStatus();
    }
}
