package cl.bancoxyz.batch.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Permite DEMOSTRAR el manejo de fallos (útil para pruebas y para el video):
 *  - simularFalloTransitorio=true : las 2 primeras escrituras fallan como si se cayera la conexión
 *    a la BD -> se ve cómo Spring Batch reintenta y el job termina bien.
 *  - simularFalloCritico=true     : la primera ejecución falla con un error no recuperable ->
 *    el job queda FAILED y la política de reejecución automática lo reinicia desde donde quedó.
 * Sin esos parámetros no hace nada.
 */
@Component
public class SimuladorFallos {

    private static final Logger log = LoggerFactory.getLogger(SimuladorFallos.class);

    private final JobExplorer explorer;
    private final Map<Long, AtomicInteger> fallosPorEjecucion = new ConcurrentHashMap<>();

    public SimuladorFallos(JobExplorer explorer) {
        this.explorer = explorer;
    }

    public void antesDeEscribir(StepExecution paso) {
        JobParameters p = paso.getJobParameters();
        if ("true".equals(p.getString("simularFalloTransitorio"))) {
            int n = fallosPorEjecucion.computeIfAbsent(paso.getJobExecutionId(), k -> new AtomicInteger()).incrementAndGet();
            if (n <= 2) {
                log.warn("[SIMULACION] Falla transitoria #{} de conexión a la base de datos", n);
                throw new TransientDataAccessResourceException("Simulación: conexión a la base de datos perdida (falla " + n + ")");
            }
        }
        if ("true".equals(p.getString("simularFalloCritico"))) {
            int ejecuciones = explorer.getJobExecutions(paso.getJobExecution().getJobInstance()).size();
            if (ejecuciones == 1) {
                log.error("[SIMULACION] Falla crítica en la primera ejecución del job");
                throw new IllegalStateException("Simulación: falla crítica (almacenamiento no disponible)");
            }
        }
    }
}
