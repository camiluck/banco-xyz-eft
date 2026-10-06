package cl.bancoxyz.batch.transacciones;

import cl.bancoxyz.batch.comun.*;
import cl.bancoxyz.batch.config.BatchInfraConfig;
import cl.bancoxyz.batch.config.BatchProperties;
import cl.bancoxyz.batch.config.FabricaPasos;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * JOB 1 - Reporte de Transacciones Diarias.
 *   Paso 1 (particionado, paralelo): leer CSV -> validar/normalizar/detectar anomalías -> guardar en transaccion_diaria
 *   Paso 2 (tasklet): resumen por día + reportes CSV
 * Si el paso 1 falla, el job termina FAILED (no se genera un resumen incompleto).
 */
@Configuration
public class TransaccionesJobConfig {

    public static final String JOB = "transaccionesDiariasJob";
    private static final String ARCHIVO = "movimientos_financieros_diarios.csv";

    @Bean
    Job transaccionesDiariasJob(JobRepository jobRepository, Step transaccionesCargaStep, Step transaccionesResumenStep,
                                ReejecucionAutomaticaListener reejecucion) {
        return new JobBuilder(JOB, jobRepository)
                .listener(reejecucion)
                .start(transaccionesCargaStep).on("FAILED").fail()
                .from(transaccionesCargaStep).on("*").to(transaccionesResumenStep)
                .end()
                .build();
    }

    @Bean
    Step transaccionesCargaStep(FabricaPasos fabrica, MultiResourcePartitioner transaccionesParticionador,
                                Step transaccionesArchivoStep) {
        return fabrica.pasoParticionado("transaccionesCargaStep", transaccionesParticionador, transaccionesArchivoStep);
    }

    @Bean
    Step transaccionesArchivoStep(FabricaPasos fabrica, FlatFileItemReader<MovimientoDiarioCsv> transaccionesReader,
                                  TransaccionProcessor transaccionesProcessor,
                                  ItemWriter<TransaccionDiaria> transaccionesWriter,
                                  RegistroRechazoListener transaccionesRechazos) {
        return fabrica.pasoTolerante("transaccionesArchivoStep", transaccionesReader, transaccionesProcessor,
                transaccionesWriter, transaccionesRechazos);
    }

    @Bean
    Step transaccionesResumenStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                  ResumenDiarioTasklet resumenDiarioTasklet) {
        return new StepBuilder("transaccionesResumenStep", jobRepository)
                .tasklet(resumenDiarioTasklet, tx)
                .build();
    }

    // ----------------------------------------------------------------- componentes con alcance de job/paso

    @Bean
    @JobScope
    MultiResourcePartitioner transaccionesParticionador(BatchProperties props,
                                                        @Value("#{jobParameters['dataset'] ?: 'semana_3'}") String dataset)
            throws IOException {
        return BatchInfraConfig.particionadorPorArchivo(props, dataset, ARCHIVO);
    }

    @Bean
    @StepScope
    FlatFileItemReader<MovimientoDiarioCsv> transaccionesReader(@Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new FlatFileItemReaderBuilder<MovimientoDiarioCsv>()
                .name("transaccionesReader")
                .resource(new DefaultResourceLoader().getResource(archivo))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .delimited()
                .names(MovimientoDiarioCsv.CAMPOS)
                .fieldSetMapper(fs -> new MovimientoDiarioCsv(fs.readString("id"), fs.readString("fecha"),
                        fs.readString("monto"), fs.readString("tipo")))
                .build();
    }

    @Bean
    @StepScope
    TransaccionProcessor transaccionesProcessor(@Value("#{stepExecution}") StepExecution paso,
                                                @Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new TransaccionProcessor(paso.getJobExecution().getJobInstance().getInstanceId(), Archivos.etiqueta(archivo));
    }

    @Bean
    @StepScope
    ItemWriter<TransaccionDiaria> transaccionesWriter(DataSource dataSource, SimuladorFallos simulador,
                                                      @Value("#{stepExecution}") StepExecution paso) {
        JdbcBatchItemWriter<TransaccionDiaria> jdbcWriter = new JdbcBatchItemWriterBuilder<TransaccionDiaria>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transaccion_diaria (instancia_id, archivo, linea, registro_id, fecha, monto, tipo,
                            fecha_original, formato_fecha_corregido, es_anomalia, motivo_anomalia, procesado_en)
                        VALUES (:instanciaId, :archivo, :linea, :registroId, :fecha, :monto, :tipo, :fechaOriginal,
                            :formatoFechaCorregido, :esAnomalia, :motivoAnomalia, :procesadoEn)""")
                .itemSqlParameterSourceProvider(t -> new MapSqlParameterSource()
                        .addValue("instanciaId", t.instanciaId())
                        .addValue("archivo", t.archivo())
                        .addValue("linea", t.linea())
                        .addValue("registroId", t.registroId())
                        .addValue("fecha", Date.valueOf(t.fecha()))
                        .addValue("monto", t.monto())
                        .addValue("tipo", t.tipo())
                        .addValue("fechaOriginal", t.fechaOriginal())
                        .addValue("formatoFechaCorregido", t.formatoFechaCorregido())
                        .addValue("esAnomalia", t.esAnomalia())
                        .addValue("motivoAnomalia", t.motivoAnomalia())
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
    RegistroRechazoListener transaccionesRechazos(JdbcTemplate jdbc, @Value("#{stepExecution}") StepExecution paso,
                                                  @Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new RegistroRechazoListener(jdbc, paso.getJobExecution().getJobInstance().getInstanceId(), JOB,
                Archivos.etiqueta(archivo));
    }

    @Bean
    @StepScope
    ResumenDiarioTasklet resumenDiarioTasklet(JdbcTemplate jdbc, BatchProperties props,
                                              @Value("#{stepExecution}") StepExecution paso) {
        return new ResumenDiarioTasklet(jdbc, paso.getJobExecution().getJobInstance().getInstanceId(),
                Path.of(props.outputDir()));
    }
}
