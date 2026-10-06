package cl.bancoxyz.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.time.YearMonth;

/**
 * Reemplaza al cron del mainframe: ejecuta cada job en su horario (activar con BATCH_PROGRAMACION=true).
 * Diario: transacciones | Mensual: intereses del mes anterior | Anual: estados de cuenta del año anterior.
 */
@Component
@ConditionalOnProperty(name = "batch.programacion.habilitada", havingValue = "true")
public class ProgramacionJobs {

    private static final Logger log = LoggerFactory.getLogger(ProgramacionJobs.class);

    private final JobLauncher launcher;
    private final Job transacciones;
    private final Job intereses;
    private final Job estados;
    private final String dataset;

    public ProgramacionJobs(JobLauncher launcher,
                            @Qualifier("transaccionesDiariasJob") Job transacciones,
                            @Qualifier("interesesMensualesJob") Job intereses,
                            @Qualifier("estadosCuentaAnualesJob") Job estados,
                            @Value("${batch.programacion.dataset:semana_3}") String dataset) {
        this.launcher = launcher;
        this.transacciones = transacciones;
        this.intereses = intereses;
        this.estados = estados;
        this.dataset = dataset;
    }

    @Scheduled(cron = "${batch.programacion.transacciones-cron}")
    public void diario() {
        lanzar(transacciones, new JobParametersBuilder());
    }

    @Scheduled(cron = "${batch.programacion.intereses-cron}")
    public void mensual() {
        lanzar(intereses, new JobParametersBuilder().addString("periodo", YearMonth.now().minusMonths(1).toString()));
    }

    @Scheduled(cron = "${batch.programacion.estados-cron}")
    public void anual() {
        lanzar(estados, new JobParametersBuilder().addString("anio", String.valueOf(Year.now().getValue() - 1)));
    }

    private void lanzar(Job job, JobParametersBuilder params) {
        try {
            params.addString("dataset", dataset).addLong("lanzamiento", System.currentTimeMillis());
            log.info("Ejecución programada de {}", job.getName());
            launcher.run(job, params.toJobParameters());
        } catch (Exception e) {
            log.error("No se pudo lanzar {}: {}", job.getName(), e.getMessage(), e);
        }
    }
}
