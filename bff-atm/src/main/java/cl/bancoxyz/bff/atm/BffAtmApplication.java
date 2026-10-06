package cl.bancoxyz.bff.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend for Frontend del canal atm: API adaptada a las necesidades de ese frontend. */
@SpringBootApplication
public class BffAtmApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffAtmApplication.class, args);
    }
}
