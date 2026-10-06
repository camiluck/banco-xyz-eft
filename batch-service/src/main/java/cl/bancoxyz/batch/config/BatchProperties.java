package cl.bancoxyz.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parámetros de los procesos batch (application.yml, prefijo "batch").
 *
 * @param inputDir        carpeta con los CSV legacy (subcarpetas semana_1, semana_2, semana_3)
 * @param outputDir       carpeta donde se escriben los reportes CSV
 * @param chunkSize       registros por transacción (commit)
 * @param hilos           particiones (archivos) procesadas en paralelo
 * @param maxOmisiones    máximo de registros inválidos tolerados por paso antes de fallar
 * @param tasaAnual       tasa de interés anual por tipo de producto
 * @param reejecucion     política de reejecución automática ante fallas críticas
 */
@ConfigurationProperties(prefix = "batch")
public record BatchProperties(String inputDir,
                              String outputDir,
                              int chunkSize,
                              int hilos,
                              int maxOmisiones,
                              Map<String, BigDecimal> tasaAnual,
                              Reejecucion reejecucion) {

    public record Reejecucion(boolean habilitada, int maxIntentos, long esperaSegundos) {
    }
}
