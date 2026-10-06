package cl.bancoxyz.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Procesos batch del Banco XYZ migrados desde COBOL/Shell a Spring Batch:
 *  1. transaccionesDiariasJob   - Reporte de Transacciones Diarias (detección de anomalías + resumen)
 *  2. interesesMensualesJob     - Cálculo de Intereses Mensuales (ahorro, préstamo, hipoteca)
 *  3. estadosCuentaAnualesJob   - Generación de Estados de Cuenta Anuales (auditoría)
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
