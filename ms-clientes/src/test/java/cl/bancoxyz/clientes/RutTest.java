package cl.bancoxyz.clientes;

import cl.bancoxyz.clientes.servicio.Rut;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RutTest {

    @Test
    void validaDigitoVerificador() {
        assertThat(Rut.esValido("12.345.678-5")).isTrue();
        assertThat(Rut.esValido("15678432-k")).isTrue();
        assertThat(Rut.esValido("9876543-3")).isTrue();
        assertThat(Rut.esValido("12345678-9")).isFalse();
        assertThat(Rut.esValido("abc")).isFalse();
    }
}
