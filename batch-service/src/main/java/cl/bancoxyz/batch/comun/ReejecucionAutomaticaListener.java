package cl.bancoxyz.batch.comun;

import cl.bancoxyz.batch.config.BatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Política de reejecución automática ante fallos críticos:
 * si un job termina FAILED, se vuelve a lanzar con los MISMOS parámetros después de una espera.
 * Spring Batch lo trata como un RESTART de la misma instancia: los pasos/particiones ya completados
 * no se repiten y el paso fallido continúa desde el último commit (no se duplican datos).
 * Se intenta como máximo batch.reejecucion.max-intentos veces.
 */
@Component
public class ReejecucionAutomaticaListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ReejecucionAutomaticaListener.class);

    private final BatchProperties.Reejecucion config;
    private final JobExplorer explorer;
    private final JobLauncher launcher;
    private final ApplicationContext contexto;
    private final ScheduledExecutorService planificador = Executors.newSingleThreadScheduledExecutor();

    public ReejecucionAutomaticaListener(BatchProperties props, JobExplorer explorer, JobLauncher launcher,
                                         ApplicationContext contexto) {
        this.config = props.reejecucion();
        this.explorer = explorer;
        this.launcher = launcher;
        this.contexto = contexto;
    }

    @Override
    public void beforeJob(JobExecution ejecucion) {
        log.info("Iniciando job {} (ejecución {}, instancia {}) con parámetros {}",
                ejecucion.getJobInstance().getJobName(), ejecucion.getId(), ejecucion.getJobInstance().getInstanceId(),
                ejecucion.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        String nombre = ejecucion.getJobInstance().getJobName();
        log.info("Job {} (ejecución {}) terminó con estado {} / {}", nombre, ejecucion.getId(),
                ejecucion.getStatus(), ejecucion.getExitStatus().getExitCode());
        if (ejecucion.getStatus() != BatchStatus.FAILED || config == null || !config.habilitada()) {
            return;
        }
        int ejecuciones = explorer.getJobExecutions(ejecucion.getJobInstance()).size();
        if (ejecuciones > config.maxIntentos()) {
            log.error("Job {} falló {} veces. Se detiene la reejecución automática: requiere revisión manual.",
                    nombre, ejecuciones);
            return;
        }
        log.warn("Job {} FALLÓ. Reejecución automática {} de {} en {} s", nombre, ejecuciones, config.maxIntentos(),
                config.esperaSegundos());
        JobParameters parametros = ejecucion.getJobParameters();
        planificador.schedule(() -> {
            try {
                Job job = contexto.getBean(nombre, Job.class);
                launcher.run(job, parametros);
            } catch (Exception e) {
                log.error("No se pudo reejecutar el job {}: {}", nombre, e.getMessage(), e);
            }
        }, config.esperaSegundos(), TimeUnit.SECONDS);
    }
}
