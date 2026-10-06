package cl.bancoxyz.bff.atm;

import cl.bancoxyz.bff.atm.integracion.BancoApiClient;
import cl.bancoxyz.bff.atm.integracion.Modelos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BffAtmTests {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    BancoApiClient api;

    private static RequestPostProcessor cajero() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.atm"));
    }

    @Test
    void terminalNoRegistradoEsBloqueado() throws Exception {
        mvc.perform(post("/atm/consulta-saldo").with(cajero()).header("X-Terminal-Id", "ATM-PIRATA")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cuentaId":1,"pin":"1234"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TERMINAL_NO_AUTORIZADO"));
        verifyNoInteractions(api);
    }

    @Test
    void consultaSaldoConPinCorrecto() throws Exception {
        when(api.validarPin(1L, "1234")).thenReturn(new PinDto(true, 3, false));
        when(api.saldo(1L)).thenReturn(new SaldoDto(1L, "XYZ-0001-10000001", new BigDecimal("150000"), "ACTIVA"));

        mvc.perform(post("/atm/consulta-saldo").with(cajero()).header("X-Terminal-Id", "ATM-TEST-01")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cuentaId":1,"pin":"1234"}"""))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.cuenta").value("****0001"))
                .andExpect(jsonPath("$.saldoDisponible").value(150000));
    }

    @Test
    void pinIncorrectoResponde401() throws Exception {
        when(api.validarPin(1L, "0000")).thenReturn(new PinDto(false, 2, false));

        mvc.perform(post("/atm/consulta-saldo").with(cajero()).header("X-Terminal-Id", "ATM-TEST-01")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cuentaId":1,"pin":"0000"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PIN_INCORRECTO"));
    }

    @Test
    void retiroNoMultiploDe5000EsRechazadoSinTocarElCore() throws Exception {
        mvc.perform(post("/atm/retiros").with(cajero()).header("X-Terminal-Id", "ATM-TEST-01")
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cuentaId":1,"pin":"1234","monto":12345}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MONTO_NO_DISPENSABLE"));
        verifyNoInteractions(api);
    }

    @Test
    void retiroExitoso() throws Exception {
        when(api.validarPin(1L, "1234")).thenReturn(new PinDto(true, 3, false));
        when(api.operar(eq("retiros"), any(), eq("ATM-ATM-TEST-01-k1"))).thenReturn(new PagoDto("ATM-ATM-TEST-01-k1",
                "RETIRO", "COMPLETADO", new BigDecimal("20000"), 1L, null, "Cajero", "ATM", null, LocalDateTime.now()));
        when(api.saldo(1L)).thenReturn(new SaldoDto(1L, "XYZ-0001-10000001", new BigDecimal("130000"), "ACTIVA"));

        mvc.perform(post("/atm/retiros").with(cajero()).header("X-Terminal-Id", "ATM-TEST-01")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"cuentaId":1,"pin":"1234","monto":20000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoEntregado").value(20000))
                .andExpect(jsonPath("$.saldoDisponible").value(130000));
    }
}
