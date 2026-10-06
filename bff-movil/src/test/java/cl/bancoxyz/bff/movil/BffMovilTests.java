package cl.bancoxyz.bff.movil;

import cl.bancoxyz.bff.movil.integracion.BancoApiClient;
import cl.bancoxyz.bff.movil.integracion.Modelos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BffMovilTests {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    BancoApiClient api;

    @Test
    void inicioDevuelveDatosLivianosYEnmascarados() throws Exception {
        when(api.cliente(1L)).thenReturn(new ClienteDto(1L, "12345678-5", "Juan Andrés", "Pérez", "j@x.cl", null, null,
                "PREMIUM", true, 1));
        when(api.cuentasDeCliente(1L)).thenReturn(List.of(
                new CuentaDto(1L, "XYZ-0001-10000001", 1L, "AHORRO", new BigDecimal("1000"), "ACTIVA", LocalDateTime.now())));
        when(api.movimientos(1L, 5)).thenReturn(List.of(new MovimientoDto(1L, 1L, "DEBITO", new BigDecimal("100"),
                new BigDecimal("900"), "R1", "Pago luz", LocalDateTime.now())));

        mvc.perform(get("/movil/clientes/1/inicio").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.movil"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.cuentas[0].num").value("****0001"))
                .andExpect(jsonPath("$.ultimas[0].signo").value("-"))
                // los campos innecesarios (rut, email, etc.) no viajan al celular
                .andExpect(jsonPath("$.rut").doesNotExist())
                .andExpect(jsonPath("$.datosParciales").doesNotExist());
    }

    @Test
    void tokenWebNoSirveEnMovil() throws Exception {
        mvc.perform(get("/movil/cuentas/1/saldo").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.web"))))
                .andExpect(status().isForbidden());
    }
}
