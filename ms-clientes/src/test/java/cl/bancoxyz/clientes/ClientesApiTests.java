package cl.bancoxyz.clientes;

import cl.bancoxyz.clientes.eventos.ConsumidorEventos;
import cl.bancoxyz.clientes.repositorio.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientesApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    ConsumidorEventos consumidor;
    @Autowired
    NotificacionRepository notificaciones;

    private static RequestPostProcessor servicioInterno() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_banco.api"));
    }

    @Test
    void obtieneClientePorId() throws Exception {
        mvc.perform(get("/api/clientes/1").with(servicioInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rut").value("12345678-5"));
    }

    @Test
    void rechazaRutInvalido() throws Exception {
        mvc.perform(post("/api/clientes").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"11222333-0","nombres":"Ana","apellidos":"Lagos","email":"ana@correo.cl"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RUT_INVALIDO"));
    }

    @Test
    void creaCliente() throws Exception {
        mvc.perform(post("/api/clientes").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rut":"11.222.333-9","nombres":"Ana","apellidos":"Lagos","email":"ana@correo.cl"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rut").value("11222333-9"))
                .andExpect(jsonPath("$.segmento").value("PERSONA"));
    }

    @Test
    void consumidorEsIdempotente() throws Exception {
        String evento = """
                {"referencia":"PAG-1","tipo":"TRANSFERENCIA","monto":15000,"clienteOrigenId":1,"clienteDestinoId":2}""";
        long antes = notificaciones.count();
        consumidor.transaccionCompletada(evento, "transacciones-completadas", 0, 42L);
        consumidor.transaccionCompletada(evento, "transacciones-completadas", 0, 42L); // re-entrega
        assertThat(notificaciones.count()).isEqualTo(antes + 2); // una para origen y otra para destino

        mvc.perform(get("/api/clientes/2/notificaciones").with(servicioInterno()))
                .andExpect(jsonPath("$[0].tipo").value("TRANSACCION"));
    }
}
