package cl.bancoxyz.batch.config;

import cl.bancoxyz.batch.comun.Archivos;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

@Configuration
public class BatchInfraConfig {

    /** Pool de hilos para procesar particiones en paralelo. */
    @Bean
    TaskExecutor batchTaskExecutor(BatchProperties props) {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(props.hilos());
        ejecutor.setMaxPoolSize(props.hilos());
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("batch-particion-");
        ejecutor.initialize();
        return ejecutor;
    }

    /**
     * Crea un particionador con un archivo por partición.
     * dataset = "semana_3" procesa esa carpeta; dataset = "*" procesa todas las semanas en paralelo.
     */
    public static MultiResourcePartitioner particionadorPorArchivo(BatchProperties props, String dataset, String archivo)
            throws IOException {
        String patron = "file:" + Path.of(props.inputDir()).toAbsolutePath().normalize().toString().replace('\\', '/')
                + "/" + dataset + "/" + archivo;
        Resource[] recursos = new PathMatchingResourcePatternResolver().getResources(patron);
        recursos = Arrays.stream(recursos).filter(Resource::exists).toArray(Resource[]::new);
        if (recursos.length == 0) {
            throw new IllegalArgumentException("No se encontraron archivos para el patrón " + patron);
        }
        Arrays.sort(recursos, (a, b) -> {
            try {
                return Archivos.etiqueta(a.getURL().toString()).compareTo(Archivos.etiqueta(b.getURL().toString()));
            } catch (IOException e) {
                return 0;
            }
        });
        MultiResourcePartitioner particionador = new MultiResourcePartitioner();
        particionador.setResources(recursos);
        return particionador;
    }
}
