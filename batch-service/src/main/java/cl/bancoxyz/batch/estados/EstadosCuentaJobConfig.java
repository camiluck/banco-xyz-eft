package cl.bancoxyz.batch.estados;

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
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JOB 3 - Generación de Estados de Cuenta Anuales (auditoría).
 *   Paso 1 (particionado, paralelo): leer movimientos del año -> limpiar/normalizar -> movimiento_anual
 *   Paso 2 (chunk): leer desde BD agrupado por cuenta -> consolidar -> estado_cuenta_anual + archivo CSV
 * Parámetro "anio" (por defecto el año anterior).
 */
@Configuration
public class EstadosCuentaJobConfig {

    public static final String JOB = "estadosCuentaAnualesJob";
    private static final String ARCHIVO = "estados_financieros_anuales.csv";

    @Bean
    Job estadosCuentaAnualesJob(JobRepository jobRepository, Step estadosCargaStep, Step estadosConsolidacionStep,
                                ReejecucionAutomaticaListener reejecucion) {
        return new JobBuilder(JOB, jobRepository)
                .listener(reejecucion)
                .start(estadosCargaStep).on("FAILED").fail()
                .from(estadosCargaStep).on("*").to(estadosConsolidacionStep)
                .end()
                .build();
    }

    @Bean
    Step estadosCargaStep(FabricaPasos fabrica, MultiResourcePartitioner estadosParticionador, Step estadosArchivoStep) {
        return fabrica.pasoParticionado("estadosCargaStep", estadosParticionador, estadosArchivoStep);
    }

    @Bean
    Step estadosArchivoStep(FabricaPasos fabrica, FlatFileItemReader<MovimientoAnualCsv> estadosReader,
                            MovimientoAnualProcessor estadosProcessor, ItemWriter<MovimientoAnual> estadosWriter,
                            RegistroRechazoListener estadosRechazos) {
        return fabrica.pasoTolerante("estadosArchivoStep", estadosReader, estadosProcessor, estadosWriter, estadosRechazos);
    }

    /** Paso 2: consolidación por cuenta, leyendo desde la base de datos con un cursor (no carga todo en memoria). */
    @Bean
    Step estadosConsolidacionStep(JobRepository jobRepository, PlatformTransactionManager tx, BatchProperties props,
                                  JdbcCursorItemReader<EstadoCuentaAnual> estadosConsolidadosReader,
                                  CompositeItemWriter<EstadoCuentaAnual> estadosCuentaWriter) {
        return new StepBuilder("estadosConsolidacionStep", jobRepository)
                .<EstadoCuentaAnual, EstadoCuentaAnual>chunk(props.chunkSize(), tx)
                .reader(estadosConsolidadosReader)
                .writer(estadosCuentaWriter)
                .listener(new ResultadoPasoListener())
                .build();
    }

    // ----------------------------------------------------------------- paso 1

    @Bean
    @JobScope
    MultiResourcePartitioner estadosParticionador(BatchProperties props,
                                                  @Value("#{jobParameters['dataset'] ?: 'semana_3'}") String dataset)
            throws IOException {
        return BatchInfraConfig.particionadorPorArchivo(props, dataset, ARCHIVO);
    }

    @Bean
    @StepScope
    FlatFileItemReader<MovimientoAnualCsv> estadosReader(@Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new FlatFileItemReaderBuilder<MovimientoAnualCsv>()
                .name("estadosReader")
                .resource(new DefaultResourceLoader().getResource(archivo))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .delimited()
                .names(MovimientoAnualCsv.CAMPOS)
                .fieldSetMapper(fs -> new MovimientoAnualCsv(fs.readString("cuenta_id"), fs.readString("fecha"),
                        fs.readString("transaccion"), fs.readString("monto"), fs.readString("descripcion")))
                .build();
    }

    @Bean
    @StepScope
    MovimientoAnualProcessor estadosProcessor(@Value("#{stepExecution}") StepExecution paso,
                                              @Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new MovimientoAnualProcessor(paso.getJobExecution().getJobInstance().getInstanceId(),
                Archivos.etiqueta(archivo), anio(paso));
    }

    @Bean
    @StepScope
    ItemWriter<MovimientoAnual> estadosWriter(DataSource dataSource, SimuladorFallos simulador,
                                              @Value("#{stepExecution}") StepExecution paso) {
        JdbcBatchItemWriter<MovimientoAnual> jdbcWriter = new JdbcBatchItemWriterBuilder<MovimientoAnual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO movimiento_anual (instancia_id, archivo, linea, cuenta_id, fecha, anio, transaccion,
                            monto, monto_original, descripcion, ajustado, procesado_en)
                        VALUES (:instanciaId, :archivo, :linea, :cuentaId, :fecha, :anio, :transaccion,
                            :monto, :montoOriginal, :descripcion, :ajustado, :procesadoEn)""")
                .itemSqlParameterSourceProvider(m -> new MapSqlParameterSource()
                        .addValue("instanciaId", m.instanciaId())
                        .addValue("archivo", m.archivo())
                        .addValue("linea", m.linea())
                        .addValue("cuentaId", m.cuentaId())
                        .addValue("fecha", Date.valueOf(m.fecha()))
                        .addValue("anio", m.anio())
                        .addValue("transaccion", m.transaccion())
                        .addValue("monto", m.monto())
                        .addValue("montoOriginal", m.montoOriginal())
                        .addValue("descripcion", m.descripcion())
                        .addValue("ajustado", m.ajustado())
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
    RegistroRechazoListener estadosRechazos(JdbcTemplate jdbc, @Value("#{stepExecution}") StepExecution paso,
                                            @Value("#{stepExecutionContext['fileName']}") String archivo) {
        return new RegistroRechazoListener(jdbc, paso.getJobExecution().getJobInstance().getInstanceId(), JOB,
                Archivos.etiqueta(archivo));
    }

    // ----------------------------------------------------------------- paso 2

    @Bean
    @StepScope
    JdbcCursorItemReader<EstadoCuentaAnual> estadosConsolidadosReader(DataSource dataSource,
                                                                      @Value("#{stepExecution}") StepExecution paso) {
        long instancia = paso.getJobExecution().getJobInstance().getInstanceId();
        int anio = anio(paso);
        return new JdbcCursorItemReaderBuilder<EstadoCuentaAnual>()
                .name("estadosConsolidadosReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT cuenta_id,
                               SUM(CASE WHEN transaccion = 'deposito' THEN monto ELSE 0 END) AS total_depositos,
                               SUM(CASE WHEN transaccion = 'retiro'   THEN monto ELSE 0 END) AS total_retiros,
                               SUM(CASE WHEN transaccion = 'compra'   THEN monto ELSE 0 END) AS total_compras,
                               SUM(CASE WHEN transaccion = 'pago'     THEN monto ELSE 0 END) AS total_pagos,
                               COUNT(*) AS cantidad,
                               SUM(CASE WHEN ajustado = TRUE THEN 1 ELSE 0 END) AS ajustados
                        FROM movimiento_anual
                        WHERE instancia_id = ? AND anio = ?
                        GROUP BY cuenta_id
                        ORDER BY cuenta_id""")
                .preparedStatementSetter(ps -> {
                    ps.setLong(1, instancia);
                    ps.setInt(2, anio);
                })
                .rowMapper((rs, n) -> {
                    BigDecimal depositos = rs.getBigDecimal("total_depositos");
                    BigDecimal retiros = rs.getBigDecimal("total_retiros");
                    BigDecimal compras = rs.getBigDecimal("total_compras");
                    BigDecimal pagos = rs.getBigDecimal("total_pagos");
                    BigDecimal cargos = retiros.add(compras).add(pagos);
                    return new EstadoCuentaAnual(instancia, anio, rs.getLong("cuenta_id"), depositos, retiros, compras,
                            pagos, cargos, depositos.subtract(cargos), rs.getLong("cantidad"), rs.getLong("ajustados"));
                })
                .build();
    }

    @Bean
    @StepScope
    CompositeItemWriter<EstadoCuentaAnual> estadosCuentaWriter(DataSource dataSource, JdbcTemplate jdbc,
                                                               BatchProperties props,
                                                               @Value("#{stepExecution}") StepExecution paso)
            throws Exception {
        long instancia = paso.getJobExecution().getJobInstance().getInstanceId();
        int anio = anio(paso);
        // Idempotencia: si el paso se reejecuta, se reemplaza el estado anterior de esta instancia
        jdbc.update("DELETE FROM estado_cuenta_anual WHERE instancia_id = ?", instancia);

        JdbcBatchItemWriter<EstadoCuentaAnual> bd = new JdbcBatchItemWriterBuilder<EstadoCuentaAnual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO estado_cuenta_anual (instancia_id, anio, cuenta_id, total_depositos, total_retiros,
                            total_compras, total_pagos, total_cargos, saldo_neto, cantidad_movimientos,
                            movimientos_ajustados, generado_en)
                        VALUES (:instancia, :anio, :cuenta, :depositos, :retiros, :compras, :pagos, :cargos, :neto,
                            :cantidad, :ajustados, :generado)""")
                .itemSqlParameterSourceProvider(e -> new MapSqlParameterSource()
                        .addValue("instancia", e.instanciaId())
                        .addValue("anio", e.anio())
                        .addValue("cuenta", e.cuentaId())
                        .addValue("depositos", e.totalDepositos())
                        .addValue("retiros", e.totalRetiros())
                        .addValue("compras", e.totalCompras())
                        .addValue("pagos", e.totalPagos())
                        .addValue("cargos", e.totalCargos())
                        .addValue("neto", e.saldoNeto())
                        .addValue("cantidad", e.cantidadMovimientos())
                        .addValue("ajustados", e.movimientosAjustados())
                        .addValue("generado", Timestamp.valueOf(LocalDateTime.now())))
                .build();
        bd.afterPropertiesSet();

        Path salida = Path.of(props.outputDir()).toAbsolutePath();
        Files.createDirectories(salida);
        FlatFileItemWriter<EstadoCuentaAnual> csv = new FlatFileItemWriterBuilder<EstadoCuentaAnual>()
                .name("estadosCuentaCsvWriter")
                .resource(new FileSystemResource(salida.resolve("estados_cuenta_" + anio + "_" + instancia + ".csv")))
                .encoding(StandardCharsets.UTF_8.name())
                .headerCallback(w -> w.write("anio,cuenta_id,total_depositos,total_retiros,total_compras,total_pagos,"
                        + "total_cargos,saldo_neto,cantidad_movimientos,movimientos_ajustados"))
                .lineAggregator(e -> String.join(",", String.valueOf(e.anio()), String.valueOf(e.cuentaId()),
                        e.totalDepositos().toPlainString(), e.totalRetiros().toPlainString(),
                        e.totalCompras().toPlainString(), e.totalPagos().toPlainString(),
                        e.totalCargos().toPlainString(), e.saldoNeto().toPlainString(),
                        String.valueOf(e.cantidadMovimientos()), String.valueOf(e.movimientosAjustados())))
                .build();

        CompositeItemWriter<EstadoCuentaAnual> compuesto = new CompositeItemWriter<>();
        compuesto.setDelegates(List.of(bd, csv));
        return compuesto;
    }

    private static int anio(StepExecution paso) {
        String valor = paso.getJobParameters().getString("anio");
        return valor == null ? java.time.Year.now().getValue() - 1 : Integer.parseInt(valor);
    }
}
