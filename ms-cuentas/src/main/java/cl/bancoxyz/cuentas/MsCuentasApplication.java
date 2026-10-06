package cl.bancoxyz.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de Gestión de Cuentas: apertura, cierre, mantenimiento, saldos, movimientos y PIN. */
@SpringBootApplication
public class MsCuentasApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsCuentasApplication.class, args);
    }
}
