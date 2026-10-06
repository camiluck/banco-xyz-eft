package cl.bancoxyz.bff.web;

import cl.bancoxyz.bff.web.integracion.BancoApiClient;
import cl.bancoxyz.bff.web.integracion.Modelos.*;
import cl.bancoxyz.bff.web.integracion.ServicioNoDisponibleException;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BffWebTests {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    BancoApiClient api;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/web/clientes/1/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtroCanalResponde403() throws Exception {
        mvc.perform(get("/web/clientes/1/resumen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.movil"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumenCompletoConDegradacionSiPagosFalla() throws Exception {
        when(api.cliente(1L)).thenReturn(new ClienteDto(1L, "12345678-5", "Juan", "Pérez", "j@x.cl", null, null,
                "PREMIUM", true, 2));
        when(api.cuentasDeCliente(1L)).thenReturn(List.of(
                new CuentaDto(1L, "XYZ-0001-10000001", 1L, "AHORRO", new BigDecimal("1000"), "ACTIVA", LocalDateTime.now()),
                new CuentaDto(2L, "XYZ-0001-10000002", 1L, "CORRIENTE", new BigDecimal("500"), "ACTIVA", LocalDateTime.now())));
        when(api.movimientos(anyLong(), anyInt())).thenReturn(List.of());
        when(api.notificaciones(1L, 10)).thenReturn(List.of());
        when(api.operaciones(anyLong(), anyInt())).thenThrow(new ServicioNoDisponibleException("pagos", new RuntimeException()));

        mvc.perform(get("/web/clientes/1/resumen").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.web"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.rut").value("12345678-5"))
                .andExpect(jsonPath("$.cuentas.length()").value(2))
                .andExpect(jsonPath("$.saldoTotal").value(1500))
                .andExpect(jsonPath("$.seccionesNoDisponibles[0]").value("operaciones"));
    }

    @Test
    void clientesCaidoResponde503() throws Exception {
        when(api.cliente(1L)).thenThrow(new ServicioNoDisponibleException("clientes", new RuntimeException()));
        when(api.cuentasDeCliente(1L)).thenReturn(List.of());
        when(api.notificaciones(1L, 10)).thenReturn(List.of());

        mvc.perform(get("/web/clientes/1/resumen").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_canal.web"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }
}
