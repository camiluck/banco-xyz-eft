package cl.bancoxyz.cuentas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CuentasApiTests {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor servicioInterno() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_banco.api"));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/cuentas/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinScopeResponde403() throws Exception {
        mvc.perform(get("/api/cuentas/1").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void listaCuentasDelCliente() throws Exception {
        mvc.perform(get("/api/cuentas").param("clienteId", "1").with(servicioInterno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void movimientoEsIdempotente() throws Exception {
        String body = """
                {"tipo":"DEBITO","monto":50000,"referencia":"TEST-IDEMP-1","descripcion":"prueba"}""";
        mvc.perform(post("/api/cuentas/2/movimientos").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoResultante").value(300000.0));
        // Mismo request (reintento): no vuelve a descontar
        mvc.perform(post("/api/cuentas/2/movimientos").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicado").value(true));
        mvc.perform(get("/api/cuentas/2/saldo").with(servicioInterno()))
                .andExpect(jsonPath("$.saldo").value(300000.0));
    }

    @Test
    void saldoInsuficienteResponde422() throws Exception {
        mvc.perform(post("/api/cuentas/4/movimientos").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"DEBITO","monto":999999,"referencia":"TEST-SALDO-1"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
    }

    @Test
    void pinSeBloqueaTrasTresIntentos() throws Exception {
        String malo = """
                {"pin":"0000"}""";
        mvc.perform(post("/api/cuentas/5/validar-pin").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.intentosRestantes").value(2));
        mvc.perform(post("/api/cuentas/5/validar-pin").with(servicioInterno())
                .contentType(MediaType.APPLICATION_JSON).content(malo));
        mvc.perform(post("/api/cuentas/5/validar-pin").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(jsonPath("$.bloqueada").value(true));
        // Ni siquiera el PIN correcto funciona con la cuenta bloqueada
        mvc.perform(post("/api/cuentas/5/validar-pin").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"pin":"1234"}"""))
                .andExpect(jsonPath("$.valido").value(false));
        mvc.perform(get("/api/cuentas/5").with(servicioInterno()))
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));
    }

    @Test
    void pinCorrectoEsValido() throws Exception {
        mvc.perform(post("/api/cuentas/3/validar-pin").with(servicioInterno())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"pin":"1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));
    }
}
