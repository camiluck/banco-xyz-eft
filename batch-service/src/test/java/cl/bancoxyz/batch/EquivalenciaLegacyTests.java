package cl.bancoxyz.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que los jobs de Spring Batch producen los MISMOS resultados que la implementación de
 * referencia de las reglas legacy (herramientas/referencia_legacy.py). Ver README para los valores.
 */
@SpringBootTest
class EquivalenciaLegacyTests {

    @Autowired
    JobLauncher launcher;
    @Autowired
    JobExplorer explorer;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    @Qualifier("transaccionesDiariasJob")
    Job transacciones;
    @Autowired
    @Qualifier("interesesMensualesJob")
    Job intereses;
    @Autowired
    @Qualifier("estadosCuentaAnualesJob")
    Job estados;

    private JobExecution ejecutar(Job job, String dataset, String... extra) throws Exception {
        JobParametersBuilder p = new JobParametersBuilder()
                .addString("dataset", dataset)
                .addLong("lanzamiento", System.nanoTime())
                .addString("periodo", "2024-12")
                .addString("anio", "2024");
        for (int i = 0; i < extra.length; i += 2) {
            p.addString(extra[i], extra[i + 1]);
        }
        return launcher.run(job, p.toJobParameters());
    }

    private static StepExecution paso(JobExecution e, String nombre) {
        return e.getStepExecutions().stream().filter(s -> s.getStepName().equals(nombre)).findFirst().orElseThrow();
    }

    private long instancia(JobExecution e) {
        return e.getJobInstance().getInstanceId();
    }

    @Test
    void transaccionesDiariasSemana1() throws Exception {
        JobExecution e = ejecutar(transacciones, "semana_1");
        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        long id = instancia(e);
        assertThat(jdbc.queryForObject("SELECT SUM(monto_creditos) FROM resumen_transacciones_diario WHERE instancia_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("4500");
        assertThat(jdbc.queryForObject("SELECT SUM(monto_debitos) FROM resumen_transacciones_diario WHERE instancia_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("5400");
        assertThat(jdbc.queryForObject("SELECT SUM(anomalias) FROM resumen_transacciones_diario WHERE instancia_id = ?",
                Integer.class, id)).isEqualTo(2);
        assertThat(Files.exists(Path.of("target/reportes-test/reporte_transacciones_diarias_" + id + ".csv"))).isTrue();
    }

    @Test
    void transaccionesDiariasSemana3() throws Exception {
        JobExecution e = ejecutar(transacciones, "semana_3");
        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution carga = paso(e, "transaccionesCargaStep");
        assertThat(carga.getWriteCount()).isEqualTo(789);
        assertThat(carga.getSkipCount()).isEqualTo(211);
        assertThat(carga.getExitStatus().getExitCode()).isEqualTo("COMPLETADO_CON_OMISIONES");
        long id = instancia(e);
        assertThat(jdbc.queryForObject("SELECT SUM(monto_creditos) FROM resumen_transacciones_diario WHERE instancia_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("244500");
        assertThat(jdbc.queryForObject("SELECT SUM(monto_debitos) FROM resumen_transacciones_diario WHERE instancia_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("284900");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaccion_diaria WHERE instancia_id = ? AND es_anomalia = TRUE",
                Integer.class, id)).isEqualTo(397);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_rechazado WHERE instancia_id = ?",
                Integer.class, id)).isEqualTo(211);
    }

    @Test
    void interesesMensuales() throws Exception {
        JobExecution s1 = ejecutar(intereses, "semana_1");
        assertThat(s1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT SUM(interes) FROM interes_mensual WHERE instancia_id = ?",
                BigDecimal.class, instancia(s1))).isEqualByComparingTo("428.00");

        JobExecution s3 = ejecutar(intereses, "semana_3");
        assertThat(s3.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution calculo = paso(s3, "interesesCalculoStep");
        assertThat(calculo.getWriteCount()).isEqualTo(340);
        assertThat(calculo.getSkipCount()).isEqualTo(660);
        assertThat(jdbc.queryForObject("SELECT SUM(interes) FROM interes_mensual WHERE instancia_id = ?",
                BigDecimal.class, instancia(s3))).isEqualByComparingTo("15440.50");
    }

    @Test
    void estadosDeCuentaAnuales() throws Exception {
        JobExecution s1 = ejecutar(estados, "semana_1");
        assertThat(s1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT SUM(saldo_neto) FROM estado_cuenta_anual WHERE instancia_id = ?",
                BigDecimal.class, instancia(s1))).isEqualByComparingTo("11400");

        JobExecution s3 = ejecutar(estados, "semana_3");
        assertThat(s3.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution carga = paso(s3, "estadosCargaStep");
        assertThat(carga.getWriteCount()).isEqualTo(759);
        assertThat(carga.getSkipCount()).isEqualTo(241);
        long id = instancia(s3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM estado_cuenta_anual WHERE instancia_id = ?",
                Integer.class, id)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT SUM(saldo_neto) FROM estado_cuenta_anual WHERE instancia_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("-418700");
    }

    @Test
    void todasLasSemanasEnParalelo() throws Exception {
        JobExecution e = ejecutar(transacciones, "*");
        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // 3 archivos = 3 particiones procesadas en paralelo
        long particiones = e.getStepExecutions().stream()
                .filter(s -> s.getStepName().startsWith("transaccionesArchivoStep:")).count();
        assertThat(particiones).isEqualTo(3);
        assertThat(paso(e, "transaccionesCargaStep").getWriteCount()).isEqualTo(10 + 9 + 789);
    }

    @Test
    void fallaTransitoriaSeReintentaYTerminaBien() throws Exception {
        JobExecution e = ejecutar(intereses, "semana_1", "simularFalloTransitorio", "true");
        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(paso(e, "interesesCalculoStep").getRollbackCount()).isGreaterThanOrEqualTo(1);
        // sin duplicados pese a los reintentos
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interes_mensual WHERE instancia_id = ?",
                Integer.class, instancia(e))).isEqualTo(8);
    }

    @Test
    void fallaCriticaSeReejecutaAutomaticamente() throws Exception {
        JobExecution primera = ejecutar(estados, "semana_1", "simularFalloCritico", "true");
        assertThat(primera.getStatus()).isEqualTo(BatchStatus.FAILED);

        // La política de reejecución relanza la misma instancia (espera configurada: 1 s)
        JobInstance instancia = primera.getJobInstance();
        BatchStatus ultimo = BatchStatus.FAILED;
        for (int i = 0; i < 60 && ultimo != BatchStatus.COMPLETED; i++) {
            Thread.sleep(500);
            List<JobExecution> ejecuciones = explorer.getJobExecutions(instancia);
            ultimo = ejecuciones.stream().filter(x -> !x.getId().equals(primera.getId()))
                    .map(JobExecution::getStatus).findFirst().orElse(BatchStatus.FAILED);
        }
        assertThat(ultimo).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT SUM(saldo_neto) FROM estado_cuenta_anual WHERE instancia_id = ?",
                BigDecimal.class, instancia.getInstanceId())).isEqualByComparingTo("11400");
    }
}
