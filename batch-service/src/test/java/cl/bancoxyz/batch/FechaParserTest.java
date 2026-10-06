package cl.bancoxyz.batch;

import cl.bancoxyz.batch.comun.FechaParser;
import cl.bancoxyz.batch.comun.RegistroInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FechaParserTest {

    @Test
    void normalizaLosCuatroFormatosDelLegacy() {
        assertThat(FechaParser.parsear("2024-03-05").fecha()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(FechaParser.parsear("2024-03-05").formatoCorregido()).isFalse();
        assertThat(FechaParser.parsear("2024/03/05").fecha()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(FechaParser.parsear("05-03-2024").fecha()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(FechaParser.parsear("05/03/2024").fecha()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(FechaParser.parsear("05/03/2024").formatoCorregido()).isTrue();
    }

    @Test
    void rechazaFechasImposibles() {
        assertThatThrownBy(() -> FechaParser.parsear("2024-02-30")).isInstanceOf(RegistroInvalidoException.class);
        assertThatThrownBy(() -> FechaParser.parsear("")).isInstanceOf(RegistroInvalidoException.class);
    }
}
