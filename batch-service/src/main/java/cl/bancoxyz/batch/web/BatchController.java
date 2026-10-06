package cl.bancoxyz.batch.web;

import cl.bancoxyz.batch.estados.EstadosCuentaJobConfig;
import cl.bancoxyz.batch.intereses.InteresesJobConfig;
import cl.bancoxyz.batch.transacciones.TransaccionesJobConfig;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.time.YearMonth;
import java.util.*;

/**
 * API de operación de los procesos batch (protegida con scope batch.admin):
 *   POST /api/batch/jobs/{job}                 lanza un job (asíncrono)
 *   GET  /api/batch/ejecuciones/{id}           estado, contadores por paso, errores
 *   POST /api/batch/ejecuciones/{id}/reiniciar reinicia manualmente una ejecución fallida
 *   GET  /api/batch/resultados/...             resultados de negocio de una instancia
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final Set<String> JOBS = Set.of(TransaccionesJobConfig.JOB, InteresesJobConfig.JOB,
            EstadosCuentaJobConfig.JOB);

    private final Map<String, Job> jobs;
    private final JobExplorer explorer;
    private final JdbcTemplate jdbc;
    private final TaskExecutorJobLauncher lanzadorAsincrono;

    public BatchController(Map<String, Job> jobs, JobExplorer explorer, JobRepository jobRepository, JdbcTemplate jdbc)
            throws Exception {
        this.jobs = jobs;
        this.explorer = explorer;
        this.jdbc = jdbc;
        this.lanzadorAsincrono = new TaskExecutorJobLauncher();
        this.lanzadorAsincrono.setJobRepository(jobRepository);
        this.lanzadorAsincrono.setTaskExecutor(new SimpleAsyncTaskExecutor("job-"));
        this.lanzadorAsincrono.afterPropertiesSet();
    }

    @GetMapping("/jobs")
    public Set<String> listarJobs() {
        return new TreeSet<>(JOBS);
    }

    /**
     * Parámetros opcionales: dataset (semana_1|semana_2|semana_3|*), periodo (yyyy-MM), anio (yyyy),
     * simularFalloTransitorio, simularFalloCritico.
     */
    @PostMapping("/jobs/{nombre}")
    public ResponseEntity<Map<String, Object>> lanzar(@PathVariable String nombre,
                                                      @RequestParam(defaultValue = "semana_3") String dataset,
                                                      @RequestParam(required = false) String periodo,
                                                      @RequestParam(required = false) Integer anio,
                                                      @RequestParam(defaultValue = "false") boolean simularFalloTransitorio,
                                                      @RequestParam(defaultValue = "false") boolean simularFalloCritico)
            throws Exception {
        Job job = buscarJob(nombre);
        JobParametersBuilder p = new JobParametersBuilder()
                .addString("dataset", dataset)
                .addLong("lanzamiento", System.currentTimeMillis());
        if (nombre.equals(InteresesJobConfig.JOB)) {
            p.addString("periodo", periodo != null ? YearMonth.parse(periodo).toString()
                    : YearMonth.now().minusMonths(1).toString());
        }
        if (nombre.equals(EstadosCuentaJobConfig.JOB)) {
            p.addString("anio", String.valueOf(anio != null ? anio : Year.now().getValue() - 1));
        }
        if (simularFalloTransitorio) {
            p.addString("simularFalloTransitorio", "true");
        }
        if (simularFalloCritico) {
            p.addString("simularFalloCritico", "true");
        }
        JobExecution ejecucion = lanzadorAsincrono.run(job, p.toJobParameters());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "ejecucionId", ejecucion.getId(),
                "instanciaId", ejecucion.getJobInstance().getInstanceId(),
                "job", nombre,
                "estado", ejecucion.getStatus().toString(),
                "consultar", "/api/batch/ejecuciones/" + ejecucion.getId()));
    }

    @GetMapping("/ejecuciones/{id}")
    public Map<String, Object> estado(@PathVariable long id) {
        JobExecution e = explorer.getJobExecution(id);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la ejecución " + id);
        }
        List<Map<String, Object>> pasos = new ArrayList<>();
        for (StepExecution s : e.getStepExecutions()) {
            Map<String, Object> paso = new LinkedHashMap<>();
            paso.put("paso", s.getStepName());
            paso.put("estado", s.getStatus().toString());
            paso.put("salida", s.getExitStatus().getExitCode());
            paso.put("leidos", s.getReadCount());
            paso.put("escritos", s.getWriteCount());
            paso.put("filtrados", s.getFilterCount());
            paso.put("omitidos", s.getSkipCount());
            paso.put("commits", s.getCommitCount());
            paso.put("rollbacks", s.getRollbackCount());
            pasos.add(paso);
        }
        pasos.sort(Comparator.comparing(m -> String.valueOf(m.get("paso"))));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ejecucionId", e.getId());
        r.put("instanciaId", e.getJobInstance().getInstanceId());
        r.put("job", e.getJobInstance().getJobName());
        r.put("estado", e.getStatus().toString());
        r.put("salida", e.getExitStatus().getExitCode());
        r.put("inicio", e.getStartTime());
        r.put("fin", e.getEndTime());
        Map<String, Object> parametros = new TreeMap<>();
        e.getJobParameters().getParameters().forEach((k, v) -> parametros.put(k, v.getValue()));
        r.put("parametros", parametros);
        r.put("intentos", explorer.getJobExecutions(e.getJobInstance()).size());
        r.put("errores", e.getAllFailureExceptions().stream().map(Throwable::getMessage).toList());
        r.put("pasos", pasos);
        return r;
    }

    @PostMapping("/ejecuciones/{id}/reiniciar")
    public ResponseEntity<Map<String, Object>> reiniciar(@PathVariable long id) throws Exception {
        JobExecution anterior = explorer.getJobExecution(id);
        if (anterior == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la ejecución " + id);
        }
        if (anterior.getStatus() != BatchStatus.FAILED && anterior.getStatus() != BatchStatus.STOPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sólo se reinician ejecuciones FAILED o STOPPED");
        }
        Job job = buscarJob(anterior.getJobInstance().getJobName());
        JobExecution nueva = lanzadorAsincrono.run(job, anterior.getJobParameters());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("ejecucionId", nueva.getId(),
                "instanciaId", nueva.getJobInstance().getInstanceId(), "estado", nueva.getStatus().toString()));
    }

    // ------------------------------------------------------------------- resultados de negocio

    @GetMapping("/resultados/{instanciaId}/transacciones")
    public List<Map<String, Object>> resumenTransacciones(@PathVariable long instanciaId) {
        return jdbc.queryForList("SELECT * FROM resumen_transacciones_diario WHERE instancia_id = ? ORDER BY fecha",
                instanciaId);
    }

    @GetMapping("/resultados/{instanciaId}/intereses")
    public List<Map<String, Object>> resumenIntereses(@PathVariable long instanciaId) {
        return jdbc.queryForList("""
                SELECT tipo, COUNT(*) AS cuentas, SUM(saldo) AS saldo_total, SUM(interes) AS interes_total
                FROM interes_mensual WHERE instancia_id = ? GROUP BY tipo ORDER BY tipo""", instanciaId);
    }

    @GetMapping("/resultados/{instanciaId}/estados")
    public List<Map<String, Object>> estadosCuenta(@PathVariable long instanciaId) {
        return jdbc.queryForList("SELECT * FROM estado_cuenta_anual WHERE instancia_id = ? ORDER BY cuenta_id",
                instanciaId);
    }

    @GetMapping("/resultados/{instanciaId}/rechazos")
    public List<Map<String, Object>> rechazos(@PathVariable long instanciaId,
                                              @RequestParam(defaultValue = "false") boolean detalle) {
        if (detalle) {
            return jdbc.queryForList("""
                    SELECT archivo, linea, contenido, motivo FROM registro_rechazado
                    WHERE instancia_id = ? ORDER BY archivo, linea""", instanciaId);
        }
        return jdbc.queryForList("""
                SELECT CASE WHEN POSITION(':' IN motivo) > 0 THEN SUBSTRING(motivo, 1, POSITION(':' IN motivo) - 1)
                            ELSE motivo END AS motivo,
                       COUNT(*) AS cantidad
                FROM registro_rechazado WHERE instancia_id = ?
                GROUP BY CASE WHEN POSITION(':' IN motivo) > 0 THEN SUBSTRING(motivo, 1, POSITION(':' IN motivo) - 1)
                              ELSE motivo END
                ORDER BY cantidad DESC""", instanciaId);
    }

    private Job buscarJob(String nombre) {
        Job job = jobs.get(nombre);
        if (job == null || !JOBS.contains(nombre)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job desconocido: " + nombre + ". Disponibles: " + JOBS);
        }
        return job;
    }
}
