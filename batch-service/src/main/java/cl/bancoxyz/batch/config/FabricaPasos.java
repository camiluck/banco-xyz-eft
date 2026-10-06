package cl.bancoxyz.batch.config;

import cl.bancoxyz.batch.comun.PoliticaOmision;
import cl.bancoxyz.batch.comun.RegistroInvalidoException;
import cl.bancoxyz.batch.comun.ResultadoPasoListener;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Construye los pasos con la MISMA política de tolerancia a fallos para los 3 jobs:
 *  - chunk: lectura/proceso/escritura en bloques transaccionales (commit cada N registros).
 *  - retry: errores transitorios de BD (conexión caída, deadlock, timeout) se reintentan 3 veces
 *           con espera exponencial (0,5 s -> 1 s -> 2 s).
 *  - skip:  registros con datos inválidos se omiten y se auditan (PoliticaOmision).
 *  - partición: cada archivo CSV es una partición y se procesan en paralelo (escalabilidad).
 */
@Component
public class FabricaPasos {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PoliticaOmision politicaOmision;
    private final TaskExecutor ejecutorParalelo;
    private final BatchProperties props;

    public FabricaPasos(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                        PoliticaOmision politicaOmision, TaskExecutor batchTaskExecutor, BatchProperties props) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.politicaOmision = politicaOmision;
        this.ejecutorParalelo = batchTaskExecutor;
        this.props = props;
    }

    /** Paso "trabajador": procesa un archivo (o un conjunto de filas) de forma tolerante a fallos. */
    public <I, O> Step pasoTolerante(String nombre, ItemReader<I> reader, ItemProcessor<I, O> processor,
                                     ItemWriter<O> writer, SkipListener<Object, Object> auditoria) {
        ExponentialBackOffPolicy espera = new ExponentialBackOffPolicy();
        espera.setInitialInterval(500);
        espera.setMultiplier(2.0);
        espera.setMaxInterval(5000);

        return new StepBuilder(nombre, jobRepository)
                .<I, O>chunk(props.chunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retry(DataAccessResourceFailureException.class)
                .retryLimit(3)
                .backOffPolicy(espera)
                .skipPolicy(politicaOmision)
                .noRollback(RegistroInvalidoException.class)
                // Los resultados del processor se reutilizan si la escritura se reintenta
                .processorNonTransactional()
                .listener(auditoria)
                .build();
    }

    /** Paso "administrador": divide el trabajo (un archivo = una partición) y lo ejecuta en paralelo. */
    public Step pasoParticionado(String nombre, Partitioner particionador, Step trabajador) {
        return new StepBuilder(nombre, jobRepository)
                .partitioner(trabajador.getName(), particionador)
                .step(trabajador)
                .gridSize(props.hilos())
                .taskExecutor(ejecutorParalelo)
                .listener(new ResultadoPasoListener())
                .build();
    }
}
