package cl.bancoxyz.batch.intereses;

import cl.bancoxyz.batch.comun.*;
import cl.bancoxyz.batch.config.BatchInfraConfig;
import cl.bancoxyz.batch.config.BatchProperties;
import cl.bancoxyz.batch.config.FabricaPasos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * JOB 2 - Cálculo de Intereses Mensuales.
 *   Paso 1 (particionado, paralelo): leer cuentas -> validar, deduplicar, calcular interés -> interes_mensual
 *   Paso 2 (tasklet): reporte CSV con el detalle y totales por tipo de producto
 * Parámetro "periodo" (yyyy-MM), por defecto el mes anterior.
 */
@Configuration
public class InteresesJobConfig {

    private static final Logger log = LoggerFactory.getLogger(InteresesJobConfig.class);
    public static final String JOB = "interesesMensualesJob";
    private static final String ARCHIVO = "intereses_trimestrales.csv";

    @Bean
    Job interesesMensualesJob(JobRepository jobRepository, Step interesesCalculoStep, Step interesesReporteStep,
                              ReejecucionAutomaticaListener reejecucion) {
        return new JobBuilder(JOB, jobRepository)
                .listener(reejecucion)
                .start(interesesCalculoStep).on("FAILED").fail()
                .from(interesesCalculoStep).on("*").to(interesesReporteStep)
                .end()
                .build();
    }

    @Bean
    Step interesesCalculoStep(FabricaPasos fabrica, MultiResourcePartitioner interesesParticionador,
                              Step interesesArchivoStep) {
        return fabrica.pasoParticionado("interesesCalculoStep", interesesParticionador, interesesArchivoStep);
    }

    @Bean
    Step interesesArchivoStep(FabricaPasos fabrica, FlatFileItemReader<CuentaInteresCsv> interesesReader,
                              InteresProcessor interesesProcessor, ItemWriter<InteresMensual> interesesWriter,
                              RegistroRechazoListener interesesRechazos) {
        return fabrica.pasoTolerante("interesesArchivoStep", interesesReader, interesesProcessor, interesesWriter,
                interesesRechazos);
    }

    @Bean
    Step interesesReporteStep(JobRepository jobRepository, PlatformTransactionManager tx, Tasklet interesesReporteTasklet) {
        return new StepBuilder("interesesReporteStep", jobRepository).tasklet(interesesReporteTasklet, tx).build();
    }

    @Bean
    @JobScope
    MultiResourcePartitioner interesesParticionador(BatchProperties props,
                                                    @Value("#{jobParameters['dataset'] ?: 'semana_3'}") String dataset)
            throws IOException {
        return BatchInfraConfig.particionadorPorArchivo(props, dataset, ARCHIVO);
    }

    @Bean
    @StepScope
    FlatFileItemReader<CuentaInteresCsv> interesesReader(@Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new FlatFileItemReaderBuilder<CuentaInteresCsv>()
                .name("interesesReader")
                .resource(new DefaultResourceLoader().getResource(archivo))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .delimited()
                .names(CuentaInteresCsv.CAMPOS)
                .fieldSetMapper(fs -> new CuentaInteresCsv(fs.readString("cuenta_id"), fs.readString("nombre"),
                        fs.readString("saldo"), fs.readString("edad"), fs.readString("tipo")))
                .build();
    }

    @Bean
    @StepScope
    InteresProcessor interesesProcessor(BatchProperties props, JdbcTemplate jdbc,
                                        @Value("#{stepExecution}") StepExecution paso,
                                        @Value("#{stepExecutionContext['fileName']}") String archivo,
                                        @Value("#{jobParameters['periodo']}") String periodo) {
        return new InteresProcessor(paso.getJobExecution().getJobInstance().getInstanceId(), Archivos.etiqueta(archivo),
                periodo == null ? java.time.YearMonth.now().minusMonths(1).toString() : periodo, props.tasaAnual(), jdbc);
    }

    @Bean
    @StepScope
    ItemWriter<InteresMensual> interesesWriter(DataSource dataSource, SimuladorFallos simulador,
                                               @Value("#{stepExecution}") StepExecution paso) {
        JdbcBatchItemWriter<InteresMensual> jdbcWriter = new JdbcBatchItemWriterBuilder<InteresMensual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO interes_mensual (instancia_id, archivo, linea, periodo, cuenta_id, nombre, tipo, edad,
                            saldo, tasa_mensual, interes, saldo_proyectado, hash_registro, procesado_en)
                        VALUES (:instanciaId, :archivo, :linea, :periodo, :cuentaId, :nombre, :tipo, :edad,
                            :saldo, :tasaMensual, :interes, :saldoProyectado, :hash, :procesadoEn)""")
                .itemSqlParameterSourceProvider(i -> new MapSqlParameterSource()
                        .addValue("instanciaId", i.instanciaId())
                        .addValue("archivo", i.archivo())
                        .addValue("linea", i.linea())
                        .addValue("periodo", i.periodo())
                        .addValue("cuentaId", i.cuentaId())
                        .addValue("nombre", i.nombre())
                        .addValue("tipo", i.tipo())
                        .addValue("edad", i.edad())
                        .addValue("saldo", i.saldo())
                        .addValue("tasaMensual", i.tasaMensual())
                        .addValue("interes", i.interes())
                        .addValue("saldoProyectado", i.saldoProyectado())
                        .addValue("hash", i.hashRegistro())
                        .addValue("procesadoEn", Timestamp.valueOf(LocalDateTime.now())))
                .build();
        jdbcWriter.afterPropertiesSet();
        return chunk -> {
            simulador.antesDeEscribir(paso);
            jdbcWriter.write(chunk);
        };
    }

    @Bean
    @StepScope
    RegistroRechazoListener interesesRechazos(JdbcTemplate jdbc, @Value("#{stepExecution}") StepExecution paso,
                                              @Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new RegistroRechazoListener(jdbc, paso.getJobExecution().getJobInstance().getInstanceId(), JOB,
                Archivos.etiqueta(archivo));
    }

    @Bean
    @StepScope
    Tasklet interesesReporteTasklet(JdbcTemplate jdbc, BatchProperties props, @Value("#{stepExecution}") StepExecution paso) {
        long instancia = paso.getJobExecution().getJobInstance().getInstanceId();
        Path salida = Path.of(props.outputDir());
        return (contribution, chunkContext) -> {
            Path detalle = ReporteCsv.exportar(jdbc, salida.resolve("intereses_mensuales_" + instancia + ".csv"), """
                    SELECT periodo, archivo, cuenta_id, nombre, tipo, saldo, tasa_mensual, interes, saldo_proyectado
                    FROM interes_mensual WHERE instancia_id = ? ORDER BY archivo, linea""", instancia);
            Path totales = ReporteCsv.exportar(jdbc, salida.resolve("intereses_totales_" + instancia + ".csv"), """
                    SELECT tipo, COUNT(*) AS cuentas, SUM(saldo) AS saldo_total, SUM(interes) AS interes_total
                    FROM interes_mensual WHERE instancia_id = ? GROUP BY tipo ORDER BY tipo""", instancia);
            log.info("Reportes de intereses generados: {} y {}", detalle, totales);
            return RepeatStatus.FINISHED;
        };
    }
}
